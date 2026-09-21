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
SOCKS_PID_FILE = LOCAL / "socks5.pid"

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

def socks_ready():
    import socket
    try:
        with socket.create_connection(("127.0.0.1", 1080), timeout=2) as connection:
            connection.sendall(b"\x05\x01\x00")
            return connection.recv(2) == b"\x05\x00"
    except OSError:
        return False

def start_socks():
    if socks_ready(): return
    with (ROOT / "socks5-relay.log").open("ab") as out, (ROOT / "socks5-relay-error.log").open("ab") as err:
        child=subprocess.Popen(
            [sys.executable, "socks5_relay.py", "--host", "127.0.0.1", "--port", "1080"],
            cwd=ROOT, stdout=out, stderr=err, stdin=subprocess.DEVNULL,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
    SOCKS_PID_FILE.write_text(str(child.pid), encoding="utf-8")
    for _ in range(20):
        if child.poll() is not None:
            raise RuntimeError("El relé SOCKS5 no inició. Revisa socks5-relay-error.log.")
        if socks_ready(): return
        time.sleep(0.25)
    raise RuntimeError("El relé SOCKS5 no respondió. Revisa socks5-relay-error.log.")

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

def stop_socks():
    pid = process_pid(SOCKS_PID_FILE, 1080, socks_ready)
    if pid is None:
        print("El relé SOCKS5 ya está detenido.")
        return
    try:
        result = subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            capture_output=True, text=True, check=False,
        )
        if result.returncode not in (0, 128):
            raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    finally:
        SOCKS_PID_FILE.unlink(missing_ok=True)
    print("Relé SOCKS5 detenido.")

def process_pid(pid_file, port, ready):
    if pid_file.exists():
        try:
            return int(pid_file.read_text(encoding="utf-8").strip())
        except ValueError:
            pid_file.unlink(missing_ok=True)
    if not ready():
        return None
    result = subprocess.run(
        ["netstat", "-ano", "-p", "tcp"], capture_output=True, text=True, check=True,
    )
    pattern = rf"(?:127\.0\.0\.1|0\.0\.0\.0|\[::\]):{port}\s+.*LISTENING"
    for line in result.stdout.splitlines():
        if re.search(pattern, line):
            return int(line.split()[-1])
    return None

def api_pid():
    return process_pid(API_PID_FILE, 8001, api_ready)

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
    start_socks()
    print("WiFiPrevent listo para el emulador en http://127.0.0.1:8001")
    print(f"WiFiPrevent listo para el teléfono en http://{local_address()}:8001")
    print("Relé SOCKS5 de desarrollo activo para el emulador en 10.0.2.2:1080")

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
        stop_socks()
        stop_api()
    elif command == "restart":
        stop_socks()
        stop_api()
        start_all()
    elif command == "status":
        print("Backend activo." if api_ready() else "Backend detenido.")
        print("SOCKS5 activo." if socks_ready() else "SOCKS5 detenido.")
    else:
        start_all()
