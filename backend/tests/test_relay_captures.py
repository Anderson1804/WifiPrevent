from uuid import uuid4

from app.api.routes import relay_captures


def test_relay_capture_contract_requires_authentication(client):
    session_id = uuid4()
    assert client.post(f"/api/v1/relay-captures/{session_id}/start").status_code == 401
    assert client.get(f"/api/v1/relay-captures/{session_id}").status_code == 401


def test_relay_capture_starts_and_returns_aggregate_metrics(client, headers, monkeypatch):
    session_id = uuid4()
    monkeypatch.setattr(
        relay_captures,
        "start_relay_session",
        lambda value: {"session_id": str(value), "status": "ready"},
    )
    monkeypatch.setattr(
        relay_captures,
        "read_relay_session",
        lambda value: {
            "session_id": str(value),
            "tcp_connections": 3,
            "udp_datagrams": 5,
            "dns_observations": 2,
            "http_observations": 0,
            "tls_or_quic_observations": 4,
            "other_observations": 2,
            "unique_destinations": 4,
        },
    )

    started = client.post(
        f"/api/v1/relay-captures/{session_id}/start", headers=headers,
    )
    metrics = client.get(f"/api/v1/relay-captures/{session_id}", headers=headers)

    assert started.status_code == 200
    assert started.json() == {"session_id": str(session_id), "status": "ready"}
    assert metrics.status_code == 200
    assert metrics.json()["tcp_connections"] == 3
    assert metrics.json()["unique_destinations"] == 4
