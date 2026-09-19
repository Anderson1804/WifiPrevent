import pytest

from app.services import evaluate_risk


@pytest.mark.parametrize(
    ("security_type", "expected_level"),
    [
        ("OPEN", "high"),
        ("WEP", "high"),
        ("WPA_WPA2_PSK", "medium"),
        ("WPA_WPA2_ENTERPRISE", "low"),
        ("WPA3_SAE", "low"),
        ("OWE", "medium"),
        ("OTHER_OR_UNKNOWN", "unknown"),
        (None, "unknown"),
    ],
)
def test_security_classification(
        security_type: str | None,
        expected_level: str,
) -> None:
    assessment = evaluate_risk(
        security_type=security_type,
        captive_portal=False,
    )

    assert assessment.level == expected_level
    assert assessment.reasons


def test_captive_portal_adds_explanation_without_changing_level() -> None:
    assessment = evaluate_risk(
        security_type="WPA3_SAE",
        captive_portal=True,
    )

    assert assessment.level == "low"
    assert len(assessment.reasons) == 2
    assert "portal cautivo" in assessment.reasons[1]