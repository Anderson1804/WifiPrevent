from dataclasses import dataclass
from typing import Literal


RiskLevel = Literal[
    "low",
    "medium",
    "high",
    "unknown",
]


@dataclass(frozen=True)
class RiskAssessment:
    level: RiskLevel
    reasons: tuple[str, ...]


def evaluate_risk(
        security_type: str | None,
        captive_portal: bool,
) -> RiskAssessment:
    reasons: list[str] = []

    if security_type == "OPEN":
        level: RiskLevel = "high"
        reasons.append(
            "La red no informa un mecanismo de cifrado."
        )

    elif security_type == "WEP":
        level = "high"
        reasons.append(
            "WEP utiliza un mecanismo de seguridad obsoleto."
        )

    elif security_type == "WPA_WPA2_PSK":
        level = "medium"
        reasons.append(
            "Android informa seguridad PSK, pero no permite "
            "distinguir con precisión entre WPA y WPA2."
        )

    elif security_type == "WPA_WPA2_ENTERPRISE":
        level = "low"
        reasons.append(
            "La red utiliza autenticación empresarial."
        )

    elif security_type == "WPA3_SAE":
        level = "low"
        reasons.append(
            "La red utiliza WPA3 con autenticación SAE."
        )

    elif security_type == "OWE":
        level = "medium"
        reasons.append(
            "OWE cifra la conexión, pero no autentica la identidad "
            "de la red."
        )

    else:
        level = "unknown"
        reasons.append(
            "No existe información suficiente sobre el tipo "
            "de seguridad."
        )

    if captive_portal:
        reasons.append(
            "La red requiere un portal cautivo. Esto no demuestra "
            "por sí solo que la red sea maliciosa."
        )

    return RiskAssessment(
        level=level,
        reasons=tuple(reasons),
    )