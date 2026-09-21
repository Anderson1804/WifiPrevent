from dataclasses import dataclass
from typing import Literal


IndicatorSeverity = Literal["info", "warning", "medium"]


@dataclass(frozen=True)
class TrafficIndicator:
    code: str
    severity: IndicatorSeverity
    title: str
    description: str


def evaluate_traffic_indicators(
        capture_mode: Literal["controlled", "full"],
        parsed_packets: int,
        unparsed_packets: int,
        ipv4_packets: int,
        ipv6_packets: int,
        http_packets: int,
) -> tuple[TrafficIndicator, ...]:
    indicators: list[TrafficIndicator] = []
    if capture_mode == "controlled":
        indicators.append(TrafficIndicator(
            code="controlled_sample",
            severity="info",
            title="Muestra controlada",
            description=(
                "Los protocolos pertenecen a una validación técnica del túnel y no "
                "representan todo el tráfico del dispositivo."
            ),
        ))
    if parsed_packets == 0:
        indicators.append(TrafficIndicator(
            code="no_parsed_packets",
            severity="warning",
            title="Sin paquetes interpretados",
            description="La sesión no produjo metadatos suficientes para clasificar protocolos.",
        ))
    if ipv6_packets > 0 and ipv4_packets == 0:
        indicators.append(TrafficIndicator(
            code="ipv6_only_sample",
            severity="info",
            title="Muestra únicamente IPv6",
            description="El entorno de ejecución entregó solamente paquetes IPv6 al túnel.",
        ))
    if unparsed_packets > 0:
        indicators.append(TrafficIndicator(
            code="unparsed_packets",
            severity="warning",
            title="Visibilidad incompleta",
            description="Algunos paquetes no pudieron interpretarse con las reglas actuales.",
        ))
    if capture_mode == "full" and http_packets > 0:
        indicators.append(TrafficIndicator(
            code="plaintext_http",
            severity="medium",
            title="Tráfico HTTP observado",
            description="Se observaron conexiones por el puerto HTTP, que no cifra por sí solo el contenido.",
        ))
    return tuple(indicators)
