import csv

import pytest

from app.services.ml_risk_classifier import FEATURE_NAMES, NUMERIC_FEATURES, normalize_features
from app.services.ml_training import CSV_COLUMNS, load_training_dataset


def _write_dataset(path, scenarios, *, extra_column=None):
    columns = list(CSV_COLUMNS)
    if extra_column:
        columns.append(extra_column)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns)
        writer.writeheader()
        for scenario_id, split, label in scenarios:
            row = {
                "scenario_id": scenario_id,
                "evaluation_split": split,
                "reference_risk_level": label,
                **{name: "0" for name in NUMERIC_FEATURES},
                "duration_seconds": "45",
                "received_packets": "70",
                "transmitted_packets": "50",
                "security_type": "WPA3_SAE",
                "captive_portal": "false",
                "capture_mode": "full",
                "relay_metrics_collected": "true",
            }
            if extra_column:
                row[extra_column] = "private-value"
            writer.writerow(row)


def test_training_dataset_normalizes_features_and_keeps_scenario_groups(tmp_path):
    path = tmp_path / "observations.csv"
    _write_dataset(path, [("authorized_01", "training", "low")])

    dataset = load_training_dataset(path)

    assert len(dataset.features[0]) == len(FEATURE_NAMES)
    assert dataset.features[0][-4:] == ["WPA3_SAE", "no", "full", "yes"]
    assert dataset.labels == ["low"]
    assert dataset.groups == ["authorized_01"]
    assert len(dataset.sha256) == 64


def test_dataset_rejects_ssid_or_other_unapproved_columns(tmp_path):
    path = tmp_path / "observations.csv"
    _write_dataset(path, [("authorized_01", "training", "low")], extra_column="ssid")

    with pytest.raises(ValueError, match="exactamente las columnas"):
        load_training_dataset(path)


def test_dataset_rejects_scenario_leakage_between_training_and_test(tmp_path):
    path = tmp_path / "observations.csv"
    _write_dataset(path, [
        ("same_scenario", "training", "low"),
        ("same_scenario", "test", "low"),
    ])

    with pytest.raises(ValueError, match="fuga de información"):
        load_training_dataset(path)


def test_dataset_rejects_rules_output_as_supervised_target(tmp_path):
    path = tmp_path / "observations.csv"
    _write_dataset(path, [("authorized_01", "training", "unknown")])

    with pytest.raises(ValueError, match="reference_risk_level"):
        load_training_dataset(path)


def test_model_features_exclude_identifiers_and_normalize_missing_metadata():
    reading = {name: 0 for name in NUMERIC_FEATURES}
    reading.update({
        "security_type": None,
        "captive_portal": None,
        "capture_mode": "full",
        "relay_metrics_collected": True,
        "ssid": "must-not-be-used",
        "session_id": "must-not-be-used",
    })

    features = normalize_features(reading)

    assert features[-4:] == ["UNKNOWN", "unknown", "full", "yes"]
    assert len(features) == len(FEATURE_NAMES)
    assert "must-not-be-used" not in features


def test_training_smoke_with_generated_fixture_only(tmp_path, monkeypatch):
    """Generated fixture verifies code only; it is not research or field data."""
    import app.services.ml_risk_classifier as classifier
    from app.services.ml_training import train_and_evaluate

    path = tmp_path / "generated_software_fixture.csv"
    scenarios = []
    for label_index, label in enumerate(("low", "medium", "high"), start=1):
        for split, count in (("training", 6), ("test", 2)):
            for number in range(count):
                scenarios.append((f"{label}_{split}_{number}", split, label, label_index))
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        for scenario_id, split, label, label_index in scenarios:
            row = {
                "scenario_id": scenario_id,
                "evaluation_split": split,
                "reference_risk_level": label,
                **{name: "0" for name in NUMERIC_FEATURES},
                "duration_seconds": "60",
                "received_bytes": str(10_000 * label_index),
                "transmitted_bytes": str(5_000 * label_index),
                "received_packets": "80",
                "transmitted_packets": "30",
                "parsed_packets": "100",
                "tcp_packets": str(20 * label_index),
                "udp_packets": str(10 * label_index),
                "relay_tcp_connections": "2",
                "relay_udp_datagrams": "2",
                "relay_dns_observations": "1",
                "relay_tls_or_quic_observations": "2",
                "relay_other_observations": "1",
                "relay_unique_destinations": "2",
                "security_type": "WPA3_SAE",
                "captive_portal": "false",
                "capture_mode": "full",
                "relay_metrics_collected": "true",
            }
            writer.writerow(row)

    model_directory = tmp_path / "models"
    manifest = train_and_evaluate(path, model_directory, random_forest_trees=10)

    assert manifest["enabled_for_inference"] is True
    assert manifest["held_out_test"]["selected_model"]["macro_f1"] > 0.9
    assert (model_directory / "risk_level_classifier.joblib").is_file()
    assert (model_directory / "risk_level_classifier.json").is_file()

    monkeypatch.setattr(classifier, "MODEL_PATH", model_directory / "risk_level_classifier.joblib")
    monkeypatch.setattr(classifier, "MANIFEST_PATH", model_directory / "risk_level_classifier.json")
    classifier._load_model_cached.cache_clear()
    reading = {name: 0 for name in NUMERIC_FEATURES}
    reading.update({
        "duration_seconds": 60,
        "received_bytes": 30_000,
        "transmitted_bytes": 15_000,
        "received_packets": 80,
        "transmitted_packets": 30,
        "tcp_packets": 60,
        "udp_packets": 30,
        "relay_tcp_connections": 2,
        "relay_udp_datagrams": 2,
        "relay_dns_observations": 1,
        "relay_tls_or_quic_observations": 2,
        "relay_other_observations": 1,
        "relay_unique_destinations": 2,
        "security_type": "WPA3_SAE",
        "captive_portal": False,
        "capture_mode": "full",
        "relay_metrics_collected": True,
    })
    prediction = classifier.predict_risk_level(reading)
    assert prediction is not None
    assert prediction.level == "high"
    assert prediction.assessment_version == f"ml-{manifest['model_version']}"
