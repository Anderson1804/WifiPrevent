import csv
import json
import threading
from io import StringIO
from uuid import uuid4
from urllib.request import Request, urlopen

import pytest
from pydantic import ValidationError

from app.schemas.temporal_capture import TemporalCapture
from app.services.temporal_event_evaluator import evaluate_temporal_events, export_event_observations, DATASET_COLUMNS
from app.services.experiment_evaluation import evaluate_experiment
from app.services.event_training import load_event_dataset, train_event_model
from app.services.lab_receiver import LabServer
from app.services.suricata_importer import import_suricata
from socks5_relay import RelayTrafficMetrics


def timeline(offsets, *, success=True, elapsed=60_000):
    return TemporalCapture(version=1, started_at_utc="2026-09-30T12:00:00Z", elapsed_ms=elapsed,
        observations=[dict(offset_ms=t, transport="tcp", category="other", destination_group=1,
                           success=success) for t in offsets])


def test_equal_totals_different_timing_produce_different_patterns():
    burst = timeline([i*500 for i in range(20)])
    distributed = timeline([i*2000 for i in range(20)])
    a = evaluate_temporal_events(burst)
    b = evaluate_temporal_events(distributed)
    assert [e.event_type for e in a] == ["connection_burst"]
    assert b == []
    assert a[0].risk_level == "unknown" and a[0].status == "pattern_observed"


def test_periodicity_is_only_a_pattern_not_a_malware_verdict():
    events = evaluate_temporal_events(timeline([i*5000 for i in range(6)]))
    assert len(events) == 1
    assert events[0].event_type == "periodic_connections"
    assert events[0].risk_level == "unknown"


def test_failed_attempts_are_separate_from_successful_connections():
    now = [0.]
    metrics = RelayTrafficMetrics(clock=lambda: now[0])
    metrics.start_session(str(uuid4()))
    for _ in range(10):
        metrics.failed_tcp("private.test", 8765)
        now[0] += .2
    snapshot = metrics.snapshot()
    assert snapshot.tcp_connections == 0
    capture = TemporalCapture.model_validate(snapshot.temporal_capture)
    assert evaluate_temporal_events(capture)[0].event_type == "repeated_failures"
    assert "private.test" not in json.dumps(snapshot.temporal_capture)


def test_capture_limits_and_epoch_prevent_cross_session_contamination():
    metrics = RelayTrafficMetrics()
    metrics.start_session(str(uuid4()))
    old = metrics.epoch
    for _ in range(2050):
        metrics.observe("tcp", "own.test", 80)
    snapshot = metrics.snapshot()
    assert len(snapshot.temporal_capture["observations"]) == 2048
    assert snapshot.temporal_capture["truncated"]
    assert evaluate_temporal_events(TemporalCapture.model_validate(snapshot.temporal_capture)) == []
    metrics.start_session(str(uuid4()))
    metrics.observe("tcp", "old.test", 80, old)
    assert metrics.snapshot().tcp_connections == 0


def test_export_preserves_normal_windows_without_labels_or_identifiers():
    rows = list(csv.DictReader(StringIO(export_event_observations(timeline([])))))
    assert len(rows) == 2
    assert rows[0]["window_start_ms"] == "0"
    assert rows[0]["reference_event_type"] == rows[0]["reference_risk_level"] == ""
    assert rows[0]["group_id"] == ""
    assert rows[0]["observation_count"] == "0.0"
    assert not any(name in rows[0] for name in ("ssid", "ip", "session_id"))


def test_schema_rejects_out_of_order_and_arbitrary_private_fields():
    with pytest.raises(ValidationError):
        timeline([1000, 0])
    data = timeline([0]).model_dump()
    data["observations"][0]["ip"] = "192.168.1.2"
    with pytest.raises(ValidationError):
        TemporalCapture.model_validate(data)


def api_reading(capture):
    return dict(session_id=str(uuid4()), duration_seconds=60, received_bytes=4000,
        transmitted_bytes=4000, received_packets=100, transmitted_packets=100,
        security_type="WPA3_SAE", capture_mode="full", relay_metrics_collected=True,
        relay_tcp_connections=len(capture.observations), relay_other_observations=len(capture.observations),
        relay_unique_destinations=1, temporal_capture=capture.model_dump())


def test_temporal_contract_roundtrip_authentication_retry_and_export(client, headers):
    payload = api_reading(timeline([i*500 for i in range(20)]))
    first = client.post("/api/v1/analysis-sessions", headers=headers, json=payload)
    assert first.status_code == 200
    assert first.json()["detected_events"][0]["status"] == "pattern_observed"
    assert client.post("/api/v1/analysis-sessions", headers=headers, json=payload).json() == first.json()
    item = client.get("/api/v1/analysis-sessions", headers=headers).json()["items"][0]
    assert item["temporal_capture"] == payload["temporal_capture"]
    assert item["detected_events"] == first.json()["detected_events"]
    path = f"/api/v1/analysis-sessions/{payload['session_id']}/event-observations"
    assert client.get(path).status_code == 401
    assert client.get(path, headers={"Authorization": "Bearer " + "f"*64}).status_code == 404
    assert client.get(path, headers=headers).status_code == 200
    experimental = client.get(path.replace("event-observations", "experiment-observation"), headers=headers)
    exported = json.loads(experimental.json()["csv_content"])
    assert "ssid" not in exported and "session_id" not in exported
    changed = payload | {"temporal_capture": timeline([i*501 for i in range(20)]).model_dump()}
    assert client.post("/api/v1/analysis-sessions", headers=headers, json=changed).status_code == 409


def test_old_records_work_and_inconsistent_new_counters_are_rejected(client, headers):
    payload = api_reading(timeline([0]))
    payload["relay_tcp_connections"] = 2
    payload["relay_other_observations"] = 2
    assert client.post("/api/v1/analysis-sessions", headers=headers, json=payload).status_code == 422
    payload.pop("temporal_capture")
    response = client.post("/api/v1/analysis-sessions", headers=headers, json=payload)
    assert response.status_code == 200 and response.json()["temporal_capture"] is None


def reference(id="ref1", status="confirmed_test_threat"):
    return dict(id=id, event_type="connection_burst", start_ms=1000, end_ms=10_000,
                risk_level="medium", reference_status=status, reviewed=True)


def detection(id="det1", risk="medium"):
    return dict(id=id, event_type="connection_burst", start_ms=1000, end_ms=9500, risk_level=risk)


def book(refs, detections):
    return dict(schema_version=1, data_kind="pilot", matching_tolerance_ms=1000, risk_rubric_version="pilot-v1",
        executions=[dict(execution_id="run1", scenario_code="S06", comparison_pair_id="p1",
            phase="postest", mechanism="wifiprevent", date_utc="2026-09-30", valid_for_evaluation=True,
            references=refs, detections=detections)])


def test_report_counts_duplicates_false_alarms_misses_and_unknown_risk():
    refs = [reference(), reference("ref2") | {"start_ms": 30_000, "end_ms": 40_000}]
    extra = detection("false") | {"start_ms": 50_000, "end_ms": 51_000}
    result = evaluate_experiment(book(refs, [detection(risk="unknown"), detection("duplicate"), extra]))["results"][0]
    assert result["detection_percentage"] == 50
    assert result["missed_threats"] == 1
    assert result["duplicate_alerts"] == 1 and result["false_alarms"] == 1
    assert result["risk_classification_percentage"] == 0


def test_no_threats_is_not_applicable_and_simulations_are_not_study_results():
    normal = evaluate_experiment(book([reference(status="normal_control")], [detection()]))["results"][0]
    assert normal["detection_percentage"] is None and normal["false_alarms"] == 1
    candidate = book([reference(status="candidate_simulation")], [detection()])
    candidate["data_kind"] = "study"
    result = evaluate_experiment(candidate)["results"][0]
    assert result["evaluation_status"] == "excluded" and result["detection_percentage"] is None


def test_receiver_logs_only_anonymous_codes_and_received_counts(tmp_path):
    path = tmp_path / "received.jsonl"
    with LabServer(("127.0.0.1", 0), path) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        try:
            request = Request(f"http://127.0.0.1:{server.server_port}/lab/software-fixture/0", data=b"synthetic", method="POST")
            with urlopen(request, timeout=3) as response:
                assert response.status == 204
        finally:
            server.shutdown(); thread.join(3)
    row = json.loads(path.read_text())
    assert row["execution_id"] == "software-fixture" and row["synthetic_bytes"] == 9
    assert "127.0.0.1" not in path.read_text() and "synthetic" not in row.values()


def test_suricata_import_requires_mapping_and_does_not_export_ip(tmp_path):
    eve = tmp_path / "eve.jsonl"; mapping = tmp_path / "mapping.json"
    eve.write_text(json.dumps(dict(event_type="alert", timestamp="2026-09-30T12:00:01Z", src_ip="192.168.1.2",
                                  alert=dict(signature_id=42, severity=1))) + "\n")
    mapping.write_text(json.dumps({"42": {"event_type": "connection_burst", "severity_to_risk": {"1": "medium"}}}))
    result = import_suricata(eve, mapping, "2026-09-30T12:00:00Z")
    assert result[0]["start_ms"] == 1000 and result[0]["risk_level"] == "medium"
    assert "src_ip" not in json.dumps(result)


def write_dataset(path, *, leak=False):
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=DATASET_COLUMNS); writer.writeheader()
        for split, count in (("training", 6), ("test", 2)):
            for group in range(count):
                for label, risk, number in (("normal", "low", 1), ("connection_burst", "medium", 20), ("periodic_connections", "high", 6)):
                    writer.writerow(dict(window_start_ms=0, window_end_ms=30_000,
                        group_id=f"{'training' if leak else split}-{group}", evaluation_split=split,
                        reference_event_type=label, reference_risk_level=risk, reference_reviewed="yes",
                        observation_count=number, duration_ms=number*500, mean_gap_ms=500,
                        gap_cv=0, failure_ratio=0, destination_count=1, tcp_ratio=1, http_ratio=0))


def test_training_rejects_leakage_and_never_activates_pilot_model(tmp_path):
    dataset = tmp_path / "fixture.csv"
    write_dataset(dataset, leak=True)
    with pytest.raises(ValueError, match="grupo relacionado"):
        load_event_dataset(dataset)
    write_dataset(dataset)
    manifest = train_event_model(dataset, tmp_path / "software_fixture_models", trees=5)
    assert manifest["enabled_for_inference"] is False and manifest["data_kind"] == "pilot"
    assert set(manifest["evaluations"]) == {"event", "risk"}
    with pytest.raises(ValueError, match="study"):
        train_event_model(dataset, tmp_path / "never", approve_inference=True, trees=5)


def test_normal_window_risk_can_be_scored_without_emitting_an_alert():
    record = book([reference(status="normal_control")], [])
    record["executions"][0]["risk_assignments"] = [{"reference_event_id": "ref1", "risk_level": "medium"}]
    result = evaluate_experiment(record)["results"][0]
    assert result["risk_classification_percentage"] == 100
    assert result["false_alarms"] == 0 and result["detection_percentage"] is None


def test_report_rejects_csv_formulas_and_changed_reference_between_conditions():
    bad = book([reference()], [])
    bad["executions"][0]["scenario_code"] = "=1+1"
    with pytest.raises(ValueError, match="fórmulas CSV"):
        evaluate_experiment(bad)
    paired = book([reference()], [detection()])
    pre = paired["executions"][0] | {"mechanism": "suricata", "phase": "pretest",
        "references": [reference() | {"risk_level": "high"}]}
    paired["executions"].append(pre)
    assert evaluate_experiment(paired)["comparisons"][0]["comparable"] is False


def test_paired_statistics_are_not_run_for_pilot_or_unreviewed_independence():
    from app.services.paired_statistics import paired_statistics
    pairs = [dict(comparable=True, detection_difference_percentage_points=100,
                  risk_difference_percentage_points=100) for _ in range(5)]
    assert paired_statistics({"data_kind": "pilot"}, pairs)["status"] == "not_performed"
    approved = dict(data_kind="study", statistics_protocol={"paired_units_independent": True,
                                                           "method": "paired-permutation-v1"})
    stats = paired_statistics(approved, pairs)
    detection_stats = stats["indicators"]["detection_difference_percentage_points"]
    assert detection_stats["p_value"] == pytest.approx(.0625)
    assert detection_stats["holm_adjusted_p_value"] == pytest.approx(.125)


def test_draft_receiver_evidence_does_not_auto_approve_a_threat(tmp_path):
    from app.services.experiment_preparation import prepare_run
    capture = tmp_path / "capture.json"; received = tmp_path / "receiver.jsonl"
    capture.write_text(json.dumps({"temporal_capture": timeline([1000]).model_dump(), "detected_events": []}))
    received.write_text(json.dumps(dict(execution_id="lab-fixture", received_at_utc="2026-09-30T12:00:01Z")))
    draft = prepare_run(capture, received, "lab-fixture", "S06", "pair-fixture")
    assert draft["valid_for_evaluation"] is False
    assert draft["references"][0]["risk_level"] is None
    assert draft["references"][0]["reference_status"] == "candidate_simulation"


def test_normal_windows_are_preserved_with_unknown_risk_without_model(client, headers):
    payload = api_reading(timeline([1000, 40_000]))
    response = client.post("/api/v1/analysis-sessions", json=payload, headers=headers)
    assert response.status_code == 200
    windows = response.json()["window_assessments"]
    assert len(windows) == 2
    assert all(r["event_type"] == "unclassified" and r["risk_level"] == "unknown" for r in windows)


def test_capture_owner_cannot_be_replaced_or_read_by_another_owner():
    import socket
    from functools import partial
    from test_socks5_relay import AsyncServerThread, receive_http
    from socks5_relay import handle_control_client
    metrics = RelayTrafficMetrics(); session_id = str(uuid4())
    with AsyncServerThread(partial(handle_control_client, metrics=metrics)) as server:
        def request(method, path, owner):
            with socket.create_connection(("127.0.0.1", server.port), timeout=3) as connection:
                connection.sendall(f"{method} {path} HTTP/1.1\r\nHost: local\r\nX-Capture-Owner: {owner}\r\n\r\n".encode())
                return receive_http(connection)
        assert b"201" in request("POST", f"/sessions/{session_id}/start", "a"*64)[0]
        metrics.observe("tcp", "private.test", 8765)
        header, content = request("GET", f"/sessions/{session_id}", "b"*64)
        assert b"404" in header and b"observations" not in content
        assert b"409" in request("POST", f"/sessions/{uuid4()}/start", "b"*64)[0]
        assert b"200" in request("GET", f"/sessions/{session_id}", "a"*64)[0]
