from uuid import uuid4

import pytest


def reading(session_id=None):
    return {
        "session_id": str(session_id or uuid4()),
        "ssid": "AndroidWifi",
        "security_type": "WPA3_SAE",
        "captive_portal": False,
        "duration_seconds": 86,
        "received_bytes": 38287,
        "transmitted_bytes": 5816,
        "received_packets": 238,
        "transmitted_packets": 42,
        "parsed_packets": 3,
        "unparsed_packets": 0,
        "ipv4_packets": 3,
        "ipv6_packets": 0,
        "tcp_packets": 0,
        "udp_packets": 3,
        "icmp_packets": 0,
        "other_transport_packets": 0,
        "dns_packets": 1,
        "http_packets": 0,
        "tls_or_quic_packets": 1,
        "unique_destinations": 3,
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
    assert response.json()["assessment_version"] == "rules-aggregate-v2"
    assert response.json()["traffic_analysis_performed"] is False
    assert response.json()["capture_mode"] == "controlled"
    assert response.json()["indicators"][0]["code"] == "controlled_sample"
    assert response.json()["sample_quality"] == "adequate"

    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]
    assert {key: item[key] for key in payload} == payload
    assert item["received_at"].endswith("Z")
    assert item["risk_level"] == "low"
    assert item["assessment_scope"] == "connection_metadata"
    assert item["assessment_version"] == "rules-aggregate-v2"
    assert item["captive_portal"] is False
    assert item["capture_mode"] == "controlled"
    assert item["indicators"]
    assert item["sample_quality"] == "adequate"


def test_short_session_reports_insufficient_sample_without_changing_history_data(client, headers):
    payload = reading() | {
        "duration_seconds": 3,
        "received_packets": 2,
        "transmitted_packets": 1,
    }

    receipt = client.post("/api/v1/analysis-sessions", json=payload, headers=headers).json()
    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]

    assert receipt["sample_quality"] == "insufficient"
    assert item["sample_quality"] == "insufficient"
    assert item["duration_seconds"] == 3


def test_analysis_retry_is_idempotent(client, headers):
    payload = reading()
    first = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    second = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    assert first.json() == second.json()
    assert len(client.get("/api/v1/analysis-sessions", headers=headers).json()["items"]) == 1


def test_full_capture_mode_is_preserved(client, headers):
    payload = reading() | {
        "capture_mode": "full",
        "parsed_packets": 0,
        "ipv4_packets": 0,
        "udp_packets": 0,
        "dns_packets": 0,
        "tls_or_quic_packets": 0,
        "unique_destinations": 0,
    }

    response = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)

    assert response.status_code == 200
    assert response.json()["capture_mode"] == "full"
    assert response.json()["assessment_scope"] == "connection_and_traffic_metadata"
    assert response.json()["traffic_analysis_performed"] is True
    codes = [indicator["code"] for indicator in response.json()["indicators"]]
    assert "aggregate_tunnel_analysis" in codes
    assert "no_parsed_packets" not in codes
    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]
    assert item["capture_mode"] == "full"
    assert item["assessment_scope"] == "connection_and_traffic_metadata"
    assert item["traffic_analysis_performed"] is True


def test_full_capture_preserves_relay_observations(client, headers):
    payload = reading() | {
        "capture_mode": "full",
        "relay_metrics_collected": True,
        "relay_tcp_connections": 4,
        "relay_udp_datagrams": 7,
        "relay_dns_observations": 3,
        "relay_http_observations": 1,
        "relay_tls_or_quic_observations": 5,
        "relay_other_observations": 2,
        "relay_unique_destinations": 6,
    }

    response = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]

    assert response.status_code == 200
    assert any(value["code"] == "plaintext_http" for value in response.json()["indicators"])
    assert item["relay_metrics_collected"] is True
    assert item["relay_tcp_connections"] == 4
    assert item["relay_udp_datagrams"] == 7
    assert item["relay_unique_destinations"] == 6
    assert item["assessment_version"] == "rules-relay-v3"


def test_captive_portal_is_preserved_and_explained(client, headers):
    payload = reading() | {"captive_portal": True}

    response = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)

    assert response.status_code == 200
    assert any("portal cautivo" in reason for reason in response.json()["risk_reasons"])
    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]
    assert item["captive_portal"] is True


def test_analysis_summary_counts_only_the_current_installation(client, headers):
    assert client.post(
        "/api/v1/analysis-sessions", json=reading(), headers=headers,
    ).status_code == 200
    assert client.post(
        "/api/v1/analysis-sessions",
        json=reading() | {"session_id": str(uuid4()), "security_type": "OPEN", "capture_mode": "full"},
        headers=headers,
    ).status_code == 200
    other_headers = {"Authorization": "Bearer " + "b" * 64}
    assert client.post(
        "/api/v1/analysis-sessions",
        json=reading() | {"session_id": str(uuid4()), "security_type": "WPA_WPA2_PSK"},
        headers=other_headers,
    ).status_code == 200

    summary = client.get("/api/v1/analysis-sessions/summary", headers=headers)

    assert summary.status_code == 200
    assert summary.json() == {
        "total_sessions": 2,
        "low_risk": 1,
        "medium_risk": 0,
        "high_risk": 1,
        "unknown_risk": 0,
        "not_evaluated": 0,
        "controlled_sessions": 1,
        "full_sessions": 1,
    }


def test_analysis_summary_is_zero_for_an_installation_without_history(client, headers):
    response = client.get("/api/v1/analysis-sessions/summary", headers=headers)

    assert response.status_code == 200
    assert all(value == 0 for value in response.json().values())


def test_analysis_history_can_filter_by_risk_and_capture_mode(client, headers):
    low_controlled = reading()
    high_full = reading() | {
        "session_id": str(uuid4()),
        "security_type": "OPEN",
        "capture_mode": "full",
    }
    assert client.post(
        "/api/v1/analysis-sessions", json=low_controlled, headers=headers,
    ).status_code == 200
    assert client.post(
        "/api/v1/analysis-sessions", json=high_full, headers=headers,
    ).status_code == 200

    response = client.get(
        "/api/v1/analysis-sessions",
        params={"risk_level": "high", "capture_mode": "full"},
        headers=headers,
    )

    assert response.status_code == 200
    assert [item["session_id"] for item in response.json()["items"]] == [
        high_full["session_id"]
    ]
    mismatched_cursor = client.get(
        "/api/v1/analysis-sessions",
        params={"risk_level": "high", "before": low_controlled["session_id"]},
        headers=headers,
    )
    assert mismatched_cursor.status_code == 404


@pytest.mark.parametrize(
    "params",
    [{"risk_level": "critical"}, {"capture_mode": "automatic"}],
)
def test_analysis_history_rejects_unknown_filters(client, headers, params):
    response = client.get("/api/v1/analysis-sessions", params=params, headers=headers)
    assert response.status_code == 422


def test_analysis_session_can_only_be_deleted_by_its_installation(client, headers):
    payload = reading()
    assert client.post(
        "/api/v1/analysis-sessions", json=payload, headers=headers,
    ).status_code == 200
    other_headers = {"Authorization": "Bearer " + "c" * 64}

    denied = client.delete(
        f"/api/v1/analysis-sessions/{payload['session_id']}", headers=other_headers,
    )
    assert denied.status_code == 404

    deleted = client.delete(
        f"/api/v1/analysis-sessions/{payload['session_id']}", headers=headers,
    )
    assert deleted.status_code == 204
    assert deleted.content == b""
    assert client.get(
        "/api/v1/analysis-sessions", headers=headers,
    ).json()["items"] == []
    assert client.get(
        "/api/v1/analysis-sessions/summary", headers=headers,
    ).json()["total_sessions"] == 0


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
        {"parsed_packets": -1},
        {"extra": "unexpected"},
        {"relay_metrics_collected": True},
        {"relay_tcp_connections": 1},
    ],
)
def test_invalid_analysis_data_is_rejected(client, headers, change):
    response = client.post("/api/v1/analysis-sessions", json=reading() | change, headers=headers)
    assert response.status_code == 422
