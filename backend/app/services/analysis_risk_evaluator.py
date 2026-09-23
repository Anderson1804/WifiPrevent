from app.services.risk_evaluator import RiskAssessment, evaluate_risk
from app.services.traffic_indicator_evaluator import is_outbound_volume_dominant


def evaluate_analysis_risk(
        security_type: str | None,
        capture_mode: str,
        duration_seconds: int,
        received_bytes: int,
        transmitted_bytes: int,
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
    if capture_mode == "controlled":
        return RiskAssessment(
            level=connection_assessment.level,
            reasons=connection_assessment.reasons + (
                "La sesión clasificó una muestra controlada de protocolos y destinos; "
                "todavía no representa todo el tráfico ni inspecciona contenido.",
            ),
        )

    reasons = connection_assessment.reasons + (
        "La captura completa evaluó el volumen agregado del túnel IPv4 sin inspeccionar contenido.",
    )
    level = connection_assessment.level
    if is_outbound_volume_dominant(duration_seconds, received_bytes, transmitted_bytes):
        if level in {"low", "unknown"}:
            level = "medium"
        reasons += (
            "El volumen enviado fue considerablemente mayor que el recibido; requiere revisión, "
            "pero no confirma por sí solo una amenaza.",
        )
    return RiskAssessment(level=level, reasons=reasons)
