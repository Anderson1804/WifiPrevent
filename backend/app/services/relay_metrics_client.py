import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import UUID


CONTROL_URL = "http://127.0.0.1:1081"


class RelayMetricsUnavailable(RuntimeError):
    pass


def _request(method: str, path: str, owner: str) -> dict:
    request = Request(f"{CONTROL_URL}{path}", method=method, headers={"X-Capture-Owner": owner})
    try:
        with urlopen(request, timeout=2) as response:
            return json.load(response)
    except (HTTPError, URLError, TimeoutError, ValueError) as exc:
        raise RelayMetricsUnavailable("El canal local de métricas no está disponible.") from exc


def start_relay_session(session_id: UUID, owner: str) -> dict:
    return _request("POST", f"/sessions/{session_id}/start", owner)


def read_relay_session(session_id: UUID, owner: str) -> dict:
    return _request("GET", f"/sessions/{session_id}", owner)
