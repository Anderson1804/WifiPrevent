from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class AnalysisSessionReading(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)

    # JSON transports UUID values as strings; the remaining fields stay strict.
    session_id: UUID = Field(strict=False)
    ssid: str | None = Field(default=None, max_length=128)
    security_type: str | None = Field(default=None, max_length=32)
    captive_portal: bool | None = None
    duration_seconds: int = Field(ge=0, le=86_400)
    received_bytes: int = Field(ge=0)
    transmitted_bytes: int = Field(ge=0)
    received_packets: int = Field(ge=0)
    transmitted_packets: int = Field(ge=0)
    parsed_packets: int = Field(default=0, ge=0)
    unparsed_packets: int = Field(default=0, ge=0)
    ipv4_packets: int = Field(default=0, ge=0)
    ipv6_packets: int = Field(default=0, ge=0)
    tcp_packets: int = Field(default=0, ge=0)
    udp_packets: int = Field(default=0, ge=0)
    icmp_packets: int = Field(default=0, ge=0)
    other_transport_packets: int = Field(default=0, ge=0)
    dns_packets: int = Field(default=0, ge=0)
    http_packets: int = Field(default=0, ge=0)
    tls_or_quic_packets: int = Field(default=0, ge=0)
    unique_destinations: int = Field(default=0, ge=0)
    capture_mode: Literal["controlled", "full"] = "controlled"


class TrafficIndicatorSchema(BaseModel):
    code: str
    severity: Literal["info", "warning", "medium"]
    title: str
    description: str


class AnalysisSessionReceipt(BaseModel):
    session_id: UUID
    received_at: datetime
    status: Literal["completed"] = "completed"
    risk_level: Literal["low", "medium", "high", "unknown"]
    risk_reasons: list[str]
    assessment_scope: Literal[
        "connection_metadata", "connection_and_traffic_metadata"
    ] = "connection_metadata"
    assessment_version: str | None = None
    traffic_analysis_performed: bool = False
    capture_mode: Literal["controlled", "full"]
    indicators: list[TrafficIndicatorSchema]
    sample_quality: Literal["insufficient", "limited", "adequate"]
    message: str = "La sesión de análisis se guardó correctamente."


class AnalysisSessionItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    session_id: UUID
    received_at: datetime
    ssid: str | None
    security_type: str | None
    captive_portal: bool | None = None
    duration_seconds: int
    received_bytes: int
    transmitted_bytes: int
    received_packets: int
    transmitted_packets: int
    parsed_packets: int = 0
    unparsed_packets: int = 0
    ipv4_packets: int = 0
    ipv6_packets: int = 0
    tcp_packets: int = 0
    udp_packets: int = 0
    icmp_packets: int = 0
    other_transport_packets: int = 0
    dns_packets: int = 0
    http_packets: int = 0
    tls_or_quic_packets: int = 0
    unique_destinations: int = 0
    risk_level: Literal["low", "medium", "high", "unknown"] | None = None
    risk_reasons: list[str] | None = None
    assessment_scope: Literal[
        "connection_metadata", "connection_and_traffic_metadata"
    ] | None = None
    assessment_version: str | None = None
    traffic_analysis_performed: bool = False
    capture_mode: Literal["controlled", "full"] = "controlled"
    indicators: list[TrafficIndicatorSchema] = Field(default_factory=list)
    sample_quality: Literal["insufficient", "limited", "adequate"] = "insufficient"


class AnalysisSessionPage(BaseModel):
    items: list[AnalysisSessionItem]
    next_before: UUID | None = None


class AnalysisHistorySummary(BaseModel):
    total_sessions: int
    low_risk: int
    medium_risk: int
    high_risk: int
    unknown_risk: int
    not_evaluated: int
    controlled_sessions: int
    full_sessions: int
