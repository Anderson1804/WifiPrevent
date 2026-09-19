from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class ConnectionReading(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        strict=True
    )

    ssid: str | None = Field(
        default=None,
        max_length=128
    )

    rssi_dbm: int | None = Field(
        default=None,
        ge=-126,
        le=-1
    )

    frequency_mhz: int | None = Field(
        default=None,
        ge=1,
        le=100000
    )

    link_speed_mbps: int | None = Field(
        default=None,
        ge=1,
        le=100000
    )

    internet_validated: bool
    captive_portal: bool

    security_type: str | None = Field(
        default=None,
        max_length=32,
    )

class Receipt(BaseModel):
    receipt_id: UUID
    received_at: datetime
    status: Literal["received"] = "received"
    risk_level: Literal[
                    "low",
                    "medium",
                    "high",
                    "unknown",
                ] | None = None
    risk_reasons: list[str] | None = None
    analysis_performed: bool = False
    message: str = (
        "Los datos se guardaron en el historial. "
    )


class HistoryItem(BaseModel):
    model_config = ConfigDict(
        from_attributes=True
    )

    receipt_id: UUID
    received_at: datetime
    ssid: str | None
    rssi_dbm: int | None
    frequency_mhz: int | None
    link_speed_mbps: int | None
    internet_validated: bool
    captive_portal: bool
    security_type: str | None
    risk_level: Literal[
                    "low",
                    "medium",
                    "high",
                    "unknown",
                ] | None = None
    risk_reasons: list[str] | None = None
    analysis_performed: bool = False


class HistoryPage(BaseModel):
    items: list[HistoryItem]
    next_before: UUID | None = None