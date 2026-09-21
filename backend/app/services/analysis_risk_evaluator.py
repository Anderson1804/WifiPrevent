from app.services.risk_evaluator import RiskAssessment, evaluate_risk


def evaluate_analysis_risk(
        security_type: str | None,
        duration_seconds: int,
        received_packets: int,
        transmitted_packets: int,
) -> RiskAssessment:
    total_packets = received_packets + transmitted_packets
    if duration_seconds < 5 or total_packets < 10:
        return RiskAssessment(
            level="unknown",
            reasons=(
                "La sesión no reunió una muestra mínima de cinco segundos y diez paquetes.",
                "La evaluación se limita a metadatos y no inspecciona el contenido del tráfico.",
            ),
        )

    connection_assessment = evaluate_risk(
        security_type=security_type,
        captive_portal=False,
    )
    return RiskAssessment(
        level=connection_assessment.level,
        reasons=connection_assessment.reasons + (
            "La sesión clasificó una muestra controlada de protocolos y destinos; todavía no representa todo el tráfico ni inspecciona contenido.",
        ),
    )
