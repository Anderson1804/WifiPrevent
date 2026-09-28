from app.services.prevention_recommender import recommend_preventive_actions


def test_open_network_and_http_produce_specific_preventive_actions():
    recommendations = recommend_preventive_actions(
        risk_level="high",
        security_type="OPEN",
        captive_portal=True,
        sample_quality="adequate",
        indicator_codes=["plaintext_http"],
    )

    assert any("red abierta" in value for value in recommendations)
    assert any("VPN" in value for value in recommendations)
    assert any("portal" in value for value in recommendations)
    assert any("HTTPS" in value for value in recommendations)


def test_low_risk_uses_basic_hygiene_when_no_specific_action_applies():
    assert recommend_preventive_actions(
        "low", "WPA3_SAE", False, "adequate", []
    ) == (
        "Mantén el sistema actualizado y confirma que los sitios utilizados conserven HTTPS.",
    )
