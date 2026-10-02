"""Bounded, anonymous connection observations; not packet content."""
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ConnectionObservation(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    offset_ms: int = Field(ge=0, le=86_400_000)
    transport: Literal["tcp", "udp"]
    category: Literal["dns", "http", "tls_quic", "other"]
    destination_group: int = Field(ge=1, le=4096)
    success: bool


class TemporalCapture(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    version: Literal[1] = 1
    started_at_utc: str
    elapsed_ms: int = Field(ge=0, le=86_400_000)
    truncated: bool = False
    dropped_observations: int = Field(default=0, ge=0)
    observations: list[ConnectionObservation] = Field(default_factory=list, max_length=2048)

    @model_validator(mode="after")
    def coherent(self):
        timestamp = datetime.fromisoformat(self.started_at_utc.replace("Z", "+00:00"))
        if timestamp.tzinfo is None or timestamp.utcoffset().total_seconds() != 0:
            raise ValueError("temporal start must use UTC")
        offsets = [row.offset_ms for row in self.observations]
        if offsets != sorted(offsets) or any(value > self.elapsed_ms for value in offsets):
            raise ValueError("observations must be ordered within the capture")
        if self.dropped_observations and not self.truncated:
            raise ValueError("dropped observations require truncated visibility")
        if any(row.transport == "udp" and not row.success for row in self.observations):
            raise ValueError("UDP observations do not establish delivery failure")
        return self


class DetectedEvent(BaseModel):
    event_id: str
    event_type: Literal["connection_burst", "periodic_connections", "repeated_failures"]
    start_ms: int
    end_ms: int
    observation_count: int
    risk_level: Literal["low", "medium", "high", "unknown"] = "unknown"
    method: str
    description: str
    status: Literal["pattern_observed"] = "pattern_observed"
    features: dict[str, float]
