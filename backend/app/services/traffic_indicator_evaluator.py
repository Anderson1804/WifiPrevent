from dataclasses import dataclass
from typing import Literal


IndicatorSeverity = Literal["info", "warning", "medium"]


@dataclass(frozen=True)
class TrafficIndicator:
    code: str
    severity: IndicatorSeverity
    title: str
    description: str


def is_outbound_volume_dominant(
        duration_seconds: int,
        received_bytes: int,
        transmitted_bytes: int,
) -> bool:
    return (
        duration_seconds >= 5
        and transmitted_bytes >= 1_048_576
        and transmitted_bytes > received_bytes * 3
    )


def evaluate_traffic_indicators(
        capture_mode: Literal["controlled", "full"],
        duration_seconds: int,
        received_bytes: int,
        transmitted_bytes: int,
        received_packets: int,
        transmitted_packets: int,
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
    if capture_mode == "full":
        indicators.append(TrafficIndicator(
            code="aggregate_tunnel_analysis",
            severity="info",
            title="Análisis agregado del túnel",
            description=(
                "La sesión evaluó duración, volumen y dirección del tráfico IPv4 "
                "reenviado, sin inspeccionar contenido ni clasificar protocolos."
            ),
        ))
    if capture_mode == "controlled" and parsed_packets == 0:
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
    total_packets = received_packets + transmitted_packets
    if capture_mode == "full" and total_packets == 0:
        indicators.append(TrafficIndicator(
            code="no_tunnel_traffic",
            severity="warning",
            title="Sin tráfico en el túnel",
            description=(
                "La captura no registró paquetes reenviados. Conviene repetirla "
                "mientras se navega o se usa una aplicación con conexión."
            ),
        ))
    if capture_mode == "full" and is_outbound_volume_dominant(
            duration_seconds, received_bytes, transmitted_bytes,
    ):
        indicators.append(TrafficIndicator(
            code="outbound_volume_dominant",
            severity="warning",
            title="Volumen de salida predominante",
            description=(
                "El volumen enviado superó ampliamente al recibido. Puede corresponder "
                "a una carga legítima; se presenta como señal para revisión, no como amenaza confirmada."
            ),
        ))
    if capture_mode == "full" and http_packets > 0:
        indicators.append(TrafficIndicator(
            code="plaintext_http",
            severity="medium",
            title="Conexión por el puerto 80",
            description=(
                "Se observó una conexión por el puerto 80. El puerto sugiere HTTP, "
                "pero esta muestra no inspecciona el contenido."
            ),
        ))
    return tuple(indicators)
