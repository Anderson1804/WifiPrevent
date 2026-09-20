"""Manage WiFiPrevent's local PostgreSQL and API processes on Windows."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.request import urlopen
import psycopg
from sqlalchemy.engine import make_url

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / ".local"
API_PID_FILE = LOCAL / "api.pid"

def db_ready():
    try:
        url=make_url(json.loads((LOCAL / "app-config.json").read_text())["database_url"])
        with psycopg.connect(host=url.host, port=url.port, user=url.username,
                             password=url.password, dbname=url.database, connect_timeout=2):
            return True
    except psycopg.OperationalError:
        return False

def start_database():
    if db_ready():
        return
    with (LOCAL / "postgres-out.log").open("ab") as out, (LOCAL / "postgres-error.log").open("ab") as err:
        subprocess.Popen([str(LOCAL / "pgsql/bin/postgres.exe"), "-D", str(LOCAL / "pgdata")],
                         cwd=ROOT, stdout=out, stderr=err, stdin=subprocess.DEVNULL,
                         creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(30):
        if db_ready(): return
        time.sleep(0.5)
    raise RuntimeError("PostgreSQL no inició. Revisa .local/postgres-error.log.")

def api_ready():
    try:
        with urlopen("http://127.0.0.1:8001/health", timeout=2) as r:
            body=json.load(r)
            return body.get("service") == "wifiprevent" and body.get("version") == "0.2.0"
    except Exception:
        return False

def start_api():
    if api_ready(): return
    with (ROOT / "history-server.log").open("ab") as out, (ROOT / "history-server-error.log").open("ab") as err:
        child=subprocess.Popen([sys.executable, "-m", "uvicorn", "main:app", "--host", "0.0.0.0",
            "--port", "8001", "--no-access-log"], cwd=ROOT, stdout=out, stderr=err,
            stdin=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    API_PID_FILE.write_text(str(child.pid), encoding="utf-8")
    for _ in range(30):
        if child.poll() is not None:
            raise RuntimeError("El backend no inició. Revisa history-server-error.log; el puerto podría estar ocupado.")
        if api_ready(): return
        time.sleep(0.5)
    raise RuntimeError("El backend no respondió. Revisa history-server-error.log.")

def stop_api():
    pid = api_pid()
    if pid is None:
        print("El backend ya está detenido.")
        return

    try:
        result = subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode not in (0, 128):
            raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    finally:
        API_PID_FILE.unlink(missing_ok=True)
    print("Backend detenido.")

def api_pid():
    if API_PID_FILE.exists():
        try:
            return int(API_PID_FILE.read_text(encoding="utf-8").strip())
        except ValueError:
            API_PID_FILE.unlink(missing_ok=True)

    if not api_ready():
        return None

    result = subprocess.run(
        ["netstat", "-ano", "-p", "tcp"],
        capture_output=True,
        text=True,
        check=True,
    )
    for line in result.stdout.splitlines():
        if re.search(r"(?:127\.0\.0\.1|0\.0\.0\.0|\[::\]):8001\s+.*LISTENING", line):
            return int(line.split()[-1])
    return None

def local_address():
    import socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("192.168.18.1", 80))
        return sock.getsockname()[0]
    except OSError:
        return "IP-DE-LA-PC"
    finally:
        sock.close()

def start_all():
    start_database()
    subprocess.run([sys.executable, "-m", "alembic", "upgrade", "head"], cwd=ROOT, check=True)
    start_api()
    print("WiFiPrevent listo para el emulador en http://127.0.0.1:8001")
    print(f"WiFiPrevent listo para el teléfono en http://{local_address()}:8001")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "command",
        nargs="?",
        choices=("start", "stop", "restart", "status"),
        default="start",
    )
    command = parser.parse_args().command

    if command == "stop":
        stop_api()
    elif command == "restart":
        stop_api()
        start_all()
    elif command == "status":
        print("Backend activo." if api_ready() else "Backend detenido.")
    else:
        start_all()
