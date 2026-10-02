"""Bounded private laboratory receiver; logs codes and times, never content/IP."""
import ipaddress
import json
import re
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class LabServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 16

    def __init__(self, address, log_path: Path, allowed_client: str | None = None):
        host = ipaddress.ip_address(address[0])
        if host.version != 4 or not (host.is_loopback or host in ipaddress.ip_network("10.0.0.0/8") or
            host in ipaddress.ip_network("172.16.0.0/12") or host in ipaddress.ip_network("192.168.0.0/16")):
            raise ValueError("El receptor debe enlazarse a una IPv4 privada concreta o loopback.")
        self.log_path = log_path
        self.allowed_client = allowed_client
        self.lock = threading.Lock()
        self.requests = 0
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        # A new file per execution batch prevents silent mixing of unrelated runs.
        self.log_path.open("x", encoding="utf-8").close()
        super().__init__(address, LabHandler)


class LabHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.connection.settimeout(5)

    def log_message(self, *_):
        pass

    def do_POST(self):
        if self.server.allowed_client and self.client_address[0] != self.server.allowed_client:
            self.send_error(403); return
        match = re.fullmatch(r"/lab/([A-Za-z0-9_-]{1,64})/([0-9]{1,3})", self.path)
        if not match or self.headers.get("Transfer-Encoding") is not None:
            self.send_error(400); return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error(400); return
        if not 0 <= length <= 3 * 1024 * 1024:
            self.send_error(413); return
        with self.server.lock:
            if self.server.requests >= 1000:
                self.send_error(429); return
            self.server.requests += 1
        started = datetime.now(timezone.utc).isoformat()
        remaining = length
        while remaining:
            part = self.rfile.read(min(8192, remaining))
            if not part:
                self.send_error(400); return
            remaining -= len(part)
        record = dict(execution_id=match[1], sequence=int(match[2]), started_at_utc=started,
                      received_at_utc=datetime.now(timezone.utc).isoformat(), synthetic_bytes=length)
        with self.server.lock:
            with self.server.log_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(record, separators=(",", ":")) + "\n")
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True


def main(argv=None):
    """Standalone entry point: copy this file to Kali; no app dependencies."""
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="127.0.0.1", help="IPv4 privada asignada al receptor.")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--allowed-client", help="IPv4 de origen del relé en la ruta hacia el receptor.")
    parser.add_argument("--log", type=Path, required=True, help="Archivo JSONL nuevo por lote.")
    args = parser.parse_args(argv)
    try:
        host = ipaddress.IPv4Address(args.bind)
        if not 1 <= args.port <= 65535:
            raise ValueError("El puerto debe estar entre 1 y 65535.")
        if args.allowed_client is not None:
            args.allowed_client = str(ipaddress.IPv4Address(args.allowed_client))
        elif not host.is_loopback:
            raise ValueError("Para una IP privada, indicar --allowed-client con el origen del relé.")
        with LabServer((str(host), args.port), args.log, args.allowed_client) as server:
            print("Receptor del laboratorio activo. Ctrl+C para detenerlo.", flush=True)
            server.serve_forever()
    except KeyboardInterrupt:
        return 0
    except (ValueError, OSError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
