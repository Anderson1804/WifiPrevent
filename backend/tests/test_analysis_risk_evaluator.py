import pytest

from app.services import evaluate_analysis_risk


@pytest.mark.parametrize(
    ("security_type", "expected"),
    [
        ("OPEN", "high"),
        ("WEP", "high"),
        ("WPA_WPA2_PSK", "medium"),
        ("WPA3_SAE", "low"),
        (None, "unknown"),
    ],
)
def test_analysis_uses_security_when_sample_is_sufficient(security_type, expected):
    result = evaluate_analysis_risk(security_type, "controlled", 20, 20_000, 5_000, 100, 20)
    assert result.level == expected
    assert "muestra controlada" in result.reasons[-1]


@pytest.mark.parametrize(
    ("duration", "received", "transmitted"),
    [(4, 100, 20), (20, 5, 4), (0, 0, 0)],
)
def test_short_or_empty_sample_is_unknown(duration, received, transmitted):
    result = evaluate_analysis_risk(
        "WPA3_SAE", "controlled", duration, 20_000, 5_000, received, transmitted,
    )
    assert result.level == "unknown"
    assert "muestra mínima" in result.reasons[0]


def test_full_capture_uses_aggregate_traffic_scope():
    result = evaluate_analysis_risk(
        "WPA3_SAE", "full", 20, 20_000, 5_000, 100, 20,
    )
    assert result.level == "low"
    assert "volumen agregado" in result.reasons[-1]


def test_dominant_outbound_volume_raises_low_risk_for_review():
    result = evaluate_analysis_risk(
        "WPA3_SAE", "full", 20, 300_000, 1_200_000, 100, 100,
    )
    assert result.level == "medium"
    assert "no confirma" in result.reasons[-1]


def test_dominant_outbound_volume_does_not_reduce_high_network_risk():
    result = evaluate_analysis_risk(
        "OPEN", "full", 20, 300_000, 1_200_000, 100, 100,
    )
    assert result.level == "high"
