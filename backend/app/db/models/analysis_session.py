from datetime import datetime
from uuid import UUID

from sqlalchemy import BigInteger, Boolean, CheckConstraint, DateTime, Index, JSON, String, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base


class AnalysisSessionRecord(Base):
    __tablename__ = "analysis_sessions"
    __table_args__ = (
        Index("ix_analysis_sessions_owner_time", "owner_hash", "received_at", "session_id"),
        CheckConstraint("duration_seconds >= 0", name="ck_analysis_duration"),
        CheckConstraint("received_bytes >= 0", name="ck_analysis_rx_bytes"),
        CheckConstraint("transmitted_bytes >= 0", name="ck_analysis_tx_bytes"),
        CheckConstraint("received_packets >= 0", name="ck_analysis_rx_packets"),
        CheckConstraint("transmitted_packets >= 0", name="ck_analysis_tx_packets"),
        CheckConstraint(
            "parsed_packets >= 0 AND unparsed_packets >= 0 AND ipv4_packets >= 0 "
            "AND ipv6_packets >= 0 AND tcp_packets >= 0 AND udp_packets >= 0 "
            "AND icmp_packets >= 0 AND other_transport_packets >= 0 "
            "AND dns_packets >= 0 AND http_packets >= 0 AND tls_or_quic_packets >= 0 "
            "AND unique_destinations >= 0",
            name="ck_analysis_metadata_nonnegative",
        ),
    )

    session_id: Mapped[UUID] = mapped_column(Uuid, primary_key=True)
    owner_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ssid: Mapped[str | None] = mapped_column(String(128))
    security_type: Mapped[str | None] = mapped_column(String(32))
    duration_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    received_bytes: Mapped[int] = mapped_column(BigInteger, nullable=False)
    transmitted_bytes: Mapped[int] = mapped_column(BigInteger, nullable=False)
    received_packets: Mapped[int] = mapped_column(BigInteger, nullable=False)
    transmitted_packets: Mapped[int] = mapped_column(BigInteger, nullable=False)
    parsed_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    unparsed_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    ipv4_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    ipv6_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    tcp_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    udp_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    icmp_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    other_transport_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    dns_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    http_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    tls_or_quic_packets: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    unique_destinations: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    risk_level: Mapped[str | None] = mapped_column(String(32))
    risk_reasons: Mapped[list[str] | None] = mapped_column(JSON)
    assessment_scope: Mapped[str | None] = mapped_column(String(32))
    traffic_analysis_performed: Mapped[bool] = mapped_column(Boolean, default=False)
