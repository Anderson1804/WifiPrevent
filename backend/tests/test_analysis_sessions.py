from uuid import uuid4

import pytest


def reading(session_id=None):
    return {
        "session_id": str(session_id or uuid4()),
        "ssid": "AndroidWifi",
        "security_type": "WPA3_SAE",
        "duration_seconds": 86,
        "received_bytes": 38287,
        "transmitted_bytes": 5816,
        "received_packets": 238,
        "transmitted_packets": 42,
    }


def test_completed_session_is_saved_and_listed(client, headers):
    payload = reading()
    response = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    assert response.status_code == 200
    assert response.json()["session_id"] == payload["session_id"]
    assert response.json()["status"] == "completed"
    assert response.json()["risk_level"] == "low"
    assert response.json()["risk_reasons"]
    assert response.json()["assessment_scope"] == "connection_metadata"
    assert response.json()["traffic_analysis_performed"] is False

    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]
    assert {key: item[key] for key in payload} == payload
    assert item["received_at"].endswith("Z")
    assert item["risk_level"] == "low"
    assert item["assessment_scope"] == "connection_metadata"


def test_analysis_retry_is_idempotent(client, headers):
    payload = reading()
    first = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    second = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    assert first.json() == second.json()
    assert len(client.get("/api/v1/analysis-sessions", headers=headers).json()["items"]) == 1


def test_session_id_cannot_be_reused_with_other_metrics(client, headers):
    payload = reading()
    assert client.post("/api/v1/analysis-sessions", json=payload, headers=headers).status_code == 200
    changed = payload | {"received_bytes": payload["received_bytes"] + 1}
    assert client.post("/api/v1/analysis-sessions", json=changed, headers=headers).status_code == 409


def test_analysis_sessions_are_isolated(client, headers):
    payload = reading()
    client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    other = {"Authorization": "Bearer " + "a" * 64}
    assert client.get("/api/v1/analysis-sessions", headers=other).json()["items"] == []
    assert client.get(
        "/api/v1/analysis-sessions", params={"before": payload["session_id"]}, headers=other
    ).status_code == 404
    assert client.post("/api/v1/analysis-sessions", json=payload, headers=other).status_code == 409


@pytest.mark.parametrize(
    "change",
    [
        {"duration_seconds": -1},
        {"duration_seconds": 86_401},
        {"received_bytes": -1},
        {"transmitted_packets": -1},
        {"extra": "unexpected"},
    ],
)
def test_invalid_analysis_data_is_rejected(client, headers, change):
    response = client.post("/api/v1/analysis-sessions", json=reading() | change, headers=headers)
    assert response.status_code == 422
