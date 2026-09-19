"""Start only this project's loopback services on Windows."""
import json
from pathlib import Path
import subprocess
import sys
import time
from urllib.request import urlopen
import psycopg
from sqlalchemy.engine import make_url

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / ".local"

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
        child=subprocess.Popen([sys.executable, "-m", "uvicorn", "main:app", "--host", "127.0.0.1",
            "--port", "8001", "--no-access-log"], cwd=ROOT, stdout=out, stderr=err,
            stdin=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    for _ in range(30):
        if child.poll() is not None:
            raise RuntimeError("El backend no inició. Revisa history-server-error.log; el puerto podría estar ocupado.")
        if api_ready(): return
        time.sleep(0.5)
    raise RuntimeError("El backend no respondió. Revisa history-server-error.log.")

if __name__ == "__main__":
    start_database()
    subprocess.run([sys.executable, "-m", "alembic", "upgrade", "head"], cwd=ROOT, check=True)
    start_api()
    print("PostgreSQL y WiFiPrevent listos en http://127.0.0.1:8001")
