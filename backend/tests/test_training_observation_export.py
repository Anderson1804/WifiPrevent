import csv
import io
import secrets
from uuid import uuid4

import pytest

from app.services.ml_risk_classifier import FEATURE_NAMES
from app.services.ml_training import CSV_COLUMNS, load_training_dataset
from app.services.training_observation_exporter import export_training_observation


def reading():
    return {
        "session_id": str(uuid4()),
        "ssid": "PRIVATE_NETWORK",
        "security_type": "OPEN",
        "captive_portal": None,
        "capture_mode": "full",
        "duration_seconds": 30,
        "received_bytes": 12000,
        "transmitted_bytes": 4000,
        "received_packets": 80,
        "transmitted_packets": 20,
        "relay_metrics_collected": True,
        "relay_tcp_connections": 4,
        "relay_udp_datagrams": 7,
        "relay_dns_observations": 3,
        "relay_http_observations": 1,
        "relay_tls_or_quic_observations": 5,
        "relay_other_observations": 2,
        "relay_unique_destinations": 6,
    }


def save_and_export(client, headers, payload):
    saved = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    assert saved.status_code == 200
    return client.get(
        f"/api/v1/analysis-sessions/{payload['session_id']}/training-observation",
        headers=headers,
    )


def test_export_matches_template_without_prediction_or_identifiers(client, headers, tmp_path):
    payload = reading()
    response = save_and_export(client, headers, payload)
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert set(response.json()) == {"csv_content"}
    content = response.json()["csv_content"]
    rows = list(csv.DictReader(io.StringIO(content)))
    assert len(rows) == 1
    row = rows[0]
    assert tuple(row) == CSV_COLUMNS
    assert [row[name] for name in CSV_COLUMNS[:3]] == ["", "", ""]
    assert row["security_type"] == "OPEN"
    assert row["captive_portal"] == "unknown"
    assert row["duration_seconds"] == "30"
    assert row["relay_tls_or_quic_observations"] == "5"
    assert payload["ssid"] not in content
    assert payload["session_id"] not in content
    assert headers["Authorization"] not in content
    assert "risk_level" not in row
    assert "assessment_version" not in row
    assert "received_at" not in row

    path = tmp_path / "observation.csv"
    path.write_text(content, encoding="utf-8")
    with pytest.raises(ValueError, match="código de escenario"):
        load_training_dataset(path)
    # A synthetic reference intentionally differs from the app's high rating.
    # This is a software compatibility check, not a research observation.
    row.update(scenario_id="scenario_01", evaluation_split="training", reference_risk_level="low")
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        writer.writerow(row)
    dataset = load_training_dataset(path)
    assert dataset.labels == ["low"]
    assert dataset.groups == ["scenario_01"]
    assert len(dataset.features[0]) == len(FEATURE_NAMES)
    history = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"]
    assert len(history) == 1
    assert history[0]["risk_level"] == "high"


@pytest.mark.parametrize("change", [
    {"capture_mode": "controlled", "relay_metrics_collected": False,
     "relay_tcp_connections": 0, "relay_udp_datagrams": 0, "relay_dns_observations": 0,
     "relay_http_observations": 0, "relay_tls_or_quic_observations": 0,
     "relay_other_observations": 0, "relay_unique_destinations": 0},
    {"duration_seconds": 29},
    {"received_packets": 79},
    {"relay_metrics_collected": False, "relay_tcp_connections": 0, "relay_udp_datagrams": 0,
     "relay_dns_observations": 0, "relay_http_observations": 0,
     "relay_tls_or_quic_observations": 0, "relay_other_observations": 0,
     "relay_unique_destinations": 0},
])
def test_ineligible_session_is_not_exported(client, headers, change):
    response = save_and_export(client, headers, reading() | change)
    assert response.status_code == 422
    assert "csv_content" not in response.json()


def test_export_requires_owner_and_does_not_reveal_other_installations(client, headers):
    payload = reading()
    assert save_and_export(client, headers, payload).status_code == 200
    path = f"/api/v1/analysis-sessions/{payload['session_id']}/training-observation"
    assert client.get(path).status_code == 401
    other = {"Authorization": "Bearer " + secrets.token_hex(32)}
    assert client.get(path, headers=other).status_code == 404
    assert client.get(
        f"/api/v1/analysis-sessions/{uuid4()}/training-observation", headers=headers,
    ).status_code == 404
    assert client.delete(
        f"/api/v1/analysis-sessions/{payload['session_id']}", headers=headers,
    ).status_code == 204
    assert client.get(path, headers=headers).status_code == 404


def test_export_normalizes_unknown_security_without_exporting_free_text(client, headers):
    response = save_and_export(client, headers, reading() | {"security_type": "=1+1"})
    assert response.status_code == 200
    content = response.json()["csv_content"]
    assert "=1+1" not in content
    assert list(csv.DictReader(io.StringIO(content)))[0]["security_type"] == "UNKNOWN"


def test_historical_inconsistent_relay_counts_are_rejected():
    from app.schemas.analysis_session import AnalysisSessionReading

    values = AnalysisSessionReading.model_validate(reading()).model_dump()
    values["relay_tls_or_quic_observations"] = 999
    with pytest.raises(ValueError, match="contadores históricos"):
        export_training_observation(values)
