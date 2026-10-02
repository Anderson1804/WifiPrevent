"""The copied Kali receiver must work without FastAPI, SQLAlchemy or site packages."""
import json
import socket
import subprocess
import sys
import time
from pathlib import Path
from urllib.request import Request, urlopen

import pytest

SCRIPT = Path(__file__).resolve().parents[1] / "app" / "services" / "lab_receiver.py"


def test_standalone_receiver_without_site_packages(tmp_path):
    with socket.socket() as reserved:
        reserved.bind(("127.0.0.1", 0))
        port = reserved.getsockname()[1]
    log = tmp_path / "received.jsonl"
    process = subprocess.Popen(
        [sys.executable, "-I", "-S", str(SCRIPT), "--port", str(port),
         "--allowed-client", "127.0.0.1", "--log", str(log)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    try:
        deadline = time.monotonic() + 5
        while True:
            assert process.poll() is None, process.communicate()[1]
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=.2):
                    break
            except OSError:
                if time.monotonic() >= deadline:
                    pytest.fail("El receptor independiente no arrancó.")
                time.sleep(.05)
        request = Request(f"http://127.0.0.1:{port}/lab/standalone-fixture/0", data=b"synthetic", method="POST")
        with urlopen(request, timeout=3) as response:
            assert response.status == 204
        row = json.loads(log.read_text(encoding="utf-8"))
        assert row["execution_id"] == "standalone-fixture" and row["synthetic_bytes"] == 9
        assert "127.0.0.1" not in log.read_text(encoding="utf-8")
    finally:
        process.terminate()
        process.communicate(timeout=5)


@pytest.mark.parametrize("options", [
    ["--bind", "192.168.56.101"],
    ["--allowed-client", "not-an-ip"],
    ["--port", "0"],
    ["--bind", "0.0.0.0", "--allowed-client", "127.0.0.1"],
])
def test_standalone_rejects_invalid_or_unrestricted_configuration(tmp_path, options):
    log = tmp_path / "not-created.jsonl"
    result = subprocess.run([sys.executable, "-I", "-S", str(SCRIPT), "--log", str(log), *options],
                            capture_output=True, text=True, timeout=5)
    assert result.returncode == 2
    assert not log.exists()
