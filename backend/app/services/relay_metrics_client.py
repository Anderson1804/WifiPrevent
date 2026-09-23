import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import UUID


CONTROL_URL = "http://127.0.0.1:1081"


class RelayMetricsUnavailable(RuntimeError):
    pass


def _request(method: str, path: str) -> dict:
    request = Request(f"{CONTROL_URL}{path}", method=method)
    try:
        with urlopen(request, timeout=2) as response:
            return json.load(response)
    except (HTTPError, URLError, TimeoutError, ValueError) as exc:
        raise RelayMetricsUnavailable("El canal local de métricas no está disponible.") from exc


def start_relay_session(session_id: UUID) -> dict:
    return _request("POST", f"/sessions/{session_id}/start")


def read_relay_session(session_id: UUID) -> dict:
    return _request("GET", f"/sessions/{session_id}")
