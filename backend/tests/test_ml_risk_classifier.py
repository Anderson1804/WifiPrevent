from app.services.ml_risk_classifier import (
    NUMERIC_FEATURES,
    ModelPrediction,
    predict_risk_level,
)


class _FakeModel:
    def __init__(self):
        self.received = None

    def predict(self, rows):
        self.received = rows
        return ["high"]


def _eligible_reading():
    reading = {name: 0 for name in NUMERIC_FEATURES}
    reading.update({
        "duration_seconds": 60,
        "received_packets": 80,
        "transmitted_packets": 30,
        "security_type": "OPEN",
        "captive_portal": False,
        "capture_mode": "full",
        "relay_metrics_collected": True,
        "ssid": "excluded-from-model",
    })
    return reading


def test_predicts_only_with_a_compatible_model_for_adequate_full_capture(monkeypatch):
    import app.services.ml_risk_classifier as classifier

    fake_model = _FakeModel()
    monkeypatch.setattr(
        classifier,
        "_load_default_model",
        lambda: (fake_model, {"model_version": "risk-test123456"}),
    )

    result = predict_risk_level(_eligible_reading())

    assert result == ModelPrediction("high", "ml-risk-test123456")
    assert len(fake_model.received[0]) == len(classifier.FEATURE_NAMES)
    assert "excluded-from-model" not in fake_model.received[0]


def test_does_not_use_model_for_short_or_non_full_samples(monkeypatch):
    import app.services.ml_risk_classifier as classifier

    monkeypatch.setattr(
        classifier,
        "_load_default_model",
        lambda: (_ for _ in ()).throw(AssertionError("model must not load")),
    )
    short = _eligible_reading()
    short["duration_seconds"] = 15
    controlled = _eligible_reading()
    controlled["capture_mode"] = "controlled"

    assert predict_risk_level(short) is None
    assert predict_risk_level(controlled) is None
