from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


class RelayCaptureReady(BaseModel):
    session_id: UUID
    status: Literal["ready"]


class RelayCaptureMetrics(BaseModel):
    session_id: UUID
    tcp_connections: int = Field(ge=0)
    udp_datagrams: int = Field(ge=0)
    dns_observations: int = Field(ge=0)
    http_observations: int = Field(ge=0)
    tls_or_quic_observations: int = Field(ge=0)
    other_observations: int = Field(ge=0)
    unique_destinations: int = Field(ge=0)
