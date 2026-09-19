from datetime import datetime
from uuid import UUID

from sqlalchemy import Boolean, CheckConstraint, DateTime, Index, Integer, JSON, String, UniqueConstraint, Uuid
from sqlalchemy.orm import Mapped, mapped_column
from app.db.session import Base

class ConnectionCheck(Base):
    __tablename__ = "connection_checks"
    __table_args__ = (
        UniqueConstraint("owner_hash", "request_id", name="uq_checks_owner_request"),
        Index("ix_checks_owner_time", "owner_hash", "received_at", "receipt_id"),
        CheckConstraint("rssi_dbm IS NULL OR rssi_dbm BETWEEN -126 AND -1", name="ck_checks_rssi"),
    )
    receipt_id: Mapped[UUID] = mapped_column(Uuid, primary_key=True)
    owner_hash: Mapped[str] = mapped_column(String(64))
    request_id: Mapped[UUID] = mapped_column(Uuid)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    ssid: Mapped[str | None] = mapped_column(String(128))
    rssi_dbm: Mapped[int | None] = mapped_column(Integer)
    frequency_mhz: Mapped[int | None] = mapped_column(Integer)
    link_speed_mbps: Mapped[int | None] = mapped_column(Integer)
    internet_validated: Mapped[bool] = mapped_column(Boolean)
    captive_portal: Mapped[bool] = mapped_column(Boolean)
    security_type: Mapped[str | None] = mapped_column(
        String(32),
        nullable=True,
    )
    risk_level: Mapped[str | None] = mapped_column(String(32), default=None)
    risk_reasons: Mapped[list[str] | None] = mapped_column(
        JSON,
        nullable=True,
        default=None,
    )
    analysis_performed: Mapped[bool] = mapped_column(Boolean, default=False)
