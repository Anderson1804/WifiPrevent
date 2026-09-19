from uuid import uuid4
import secrets
import pytest


from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import text
from sqlalchemy.exc import OperationalError
from app.db.session import engine, get_session
from main import app
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]

VALID = {"ssid": "AndroidWifi", "rssi_dbm": -50, "frequency_mhz": 2447,
         "link_speed_mbps": 1, "internet_validated": True, "captive_portal": False, "security_type": "WPA3_SAE"}

@pytest.fixture(scope="session", autouse=True)
def migrate():
    assert engine.url.database == "wifiprevent_test"
    assert engine.url.database == "wifiprevent_test"
    command.upgrade(
        Config(str(BACKEND_ROOT / "alembic.ini")),
        "head"
    )

@pytest.fixture()
def client():
    assert engine.url.database == "wifiprevent_test"
    with engine.begin() as c:
        c.execute(text("TRUNCATE connection_checks"))
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()

@pytest.fixture()
def headers():
    return {"Authorization": "Bearer " + secrets.token_hex(32), "X-Request-ID": str(uuid4())}

def test_health(client):
    assert client.get("/health").json()["database"] == "postgresql"

def test_saved_then_read_in_new_connection(client, headers):
    response = client.post("/api/v1/connection-checks", json=VALID, headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["risk_level"] == "low"
    assert body["analysis_performed"] is True
    assert body["risk_reasons"]
    assert "ssid" not in body
    engine.dispose()
    page = client.get("/api/v1/connection-checks", headers=headers).json()
    assert page["items"][0]["receipt_id"] == body["receipt_id"]
    assert page["items"][0]["ssid"] == "AndroidWifi"
    assert page["items"][0]["received_at"].endswith("Z")
    assert page["items"][0]["risk_level"] == "low"
    assert page["items"][0]["analysis_performed"] is True
    assert page["items"][0]["risk_reasons"]

def test_null_metadata_persists(client, headers):
    assert client.post("/api/v1/connection-checks", json={
        "internet_validated": False, "captive_portal": True}, headers=headers).status_code == 200
    item = client.get("/api/v1/connection-checks", headers=headers).json()["items"][0]
    assert item["ssid"] is None and item["rssi_dbm"] is None

@pytest.mark.parametrize("change", [{"rssi_dbm": 20}, {"frequency_mhz": -1},
    {"internet_validated": "true"}, {"packet_payload": "unexpected"}, {"ssid": "x" * 129}])
def test_rejects_invalid_data_without_saving(client, headers, change):
    assert client.post("/api/v1/connection-checks", json=VALID | change, headers=headers).status_code == 422
    assert client.get("/api/v1/connection-checks", headers=headers).json()["items"] == []

def test_missing_flags(client, headers):
    assert client.post("/api/v1/connection-checks", json={}, headers=headers).status_code == 422

def test_retry_is_idempotent(client, headers):
    a = client.post("/api/v1/connection-checks", json=VALID, headers=headers).json()
    b = client.post("/api/v1/connection-checks", json=VALID, headers=headers).json()
    assert a == b
    assert len(client.get("/api/v1/connection-checks", headers=headers).json()["items"]) == 1

def test_reused_request_with_other_data_rejected(client, headers):
    client.post("/api/v1/connection-checks", json=VALID, headers=headers)
    assert client.post("/api/v1/connection-checks", json=VALID | {"rssi_dbm": -70}, headers=headers).status_code == 409
    assert client.get("/api/v1/connection-checks", headers=headers).json()["items"][0]["rssi_dbm"] == -50

def test_installations_are_isolated(client, headers):
    row = client.post("/api/v1/connection-checks", json=VALID, headers=headers).json()
    other = {"Authorization": "Bearer " + secrets.token_hex(32)}
    assert client.get("/api/v1/connection-checks", headers=other).json()["items"] == []
    assert client.get("/api/v1/connection-checks", params={"before":row["receipt_id"]}, headers=other).status_code == 404

@pytest.mark.parametrize("authorization", [None, "Bearer invalid"])
def test_auth_required(client, authorization):
    h = {} if authorization is None else {"Authorization":authorization}
    assert client.get("/api/v1/connection-checks", headers=h).status_code == 401

def test_cursor_pagination_is_stable(client, headers):
    ids=[]
    for _ in range(3):
        h=headers | {"X-Request-ID":str(uuid4())}
        ids.append(client.post("/api/v1/connection-checks", json=VALID, headers=h).json()["receipt_id"])
    first=client.get("/api/v1/connection-checks?limit=2", headers=headers).json()
    assert [x["receipt_id"] for x in first["items"]] == list(reversed(ids))[0:2]
    client.post("/api/v1/connection-checks", json=VALID, headers=headers | {"X-Request-ID":str(uuid4())})
    second=client.get("/api/v1/connection-checks", params={"limit":2,"before":first["next_before"]}, headers=headers).json()
    assert [x["receipt_id"] for x in second["items"]] == [ids[0]]
    assert second["next_before"] is None

def test_bad_pagination(client, headers):
    for params in ({"limit":0},{"limit":101},{"before":"bad"}):
        assert client.get("/api/v1/connection-checks", params=params, headers=headers).status_code == 422

def test_database_failure_does_not_claim_success(client, headers):
    def unavailable():
        raise OperationalError("test", {}, Exception("offline"))
    app.dependency_overrides[get_session] = unavailable
    r=client.post("/api/v1/connection-checks", json=VALID, headers=headers)
    assert r.status_code == 503 and "receipt_id" not in r.json()
