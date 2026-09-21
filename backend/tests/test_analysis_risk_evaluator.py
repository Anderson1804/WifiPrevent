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
    result = evaluate_analysis_risk(security_type, 20, 100, 20)
    assert result.level == expected
    assert "muestra controlada" in result.reasons[-1]


@pytest.mark.parametrize(
    ("duration", "received", "transmitted"),
    [(4, 100, 20), (20, 5, 4), (0, 0, 0)],
)
def test_short_or_empty_sample_is_unknown(duration, received, transmitted):
    result = evaluate_analysis_risk("WPA3_SAE", duration, received, transmitted)
    assert result.level == "unknown"
    assert "muestra mínima" in result.reasons[0]
