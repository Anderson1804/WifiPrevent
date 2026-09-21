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
    duration_seconds: int = Field(ge=0, le=86_400)
    received_bytes: int = Field(ge=0)
    transmitted_bytes: int = Field(ge=0)
    received_packets: int = Field(ge=0)
    transmitted_packets: int = Field(ge=0)


class AnalysisSessionReceipt(BaseModel):
    session_id: UUID
    received_at: datetime
    status: Literal["completed"] = "completed"
    message: str = "La sesión de análisis se guardó correctamente."


class AnalysisSessionItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    session_id: UUID
    received_at: datetime
    ssid: str | None
    security_type: str | None
    duration_seconds: int
    received_bytes: int
    transmitted_bytes: int
    received_packets: int
    transmitted_packets: int


class AnalysisSessionPage(BaseModel):
    items: list[AnalysisSessionItem]
    next_before: UUID | None = None
