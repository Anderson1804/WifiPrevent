"""add analysis sessions

Revision ID: 75b83592a660
Revises: 0395cfc98c19
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "75b83592a660"
down_revision: Union[str, Sequence[str], None] = "0395cfc98c19"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "analysis_sessions",
        sa.Column("session_id", sa.Uuid(), nullable=False),
        sa.Column("owner_hash", sa.String(length=64), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ssid", sa.String(length=128), nullable=True),
        sa.Column("security_type", sa.String(length=32), nullable=True),
        sa.Column("duration_seconds", sa.BigInteger(), nullable=False),
        sa.Column("received_bytes", sa.BigInteger(), nullable=False),
        sa.Column("transmitted_bytes", sa.BigInteger(), nullable=False),
        sa.Column("received_packets", sa.BigInteger(), nullable=False),
        sa.Column("transmitted_packets", sa.BigInteger(), nullable=False),
        sa.CheckConstraint("duration_seconds >= 0", name="ck_analysis_duration"),
        sa.CheckConstraint("received_bytes >= 0", name="ck_analysis_rx_bytes"),
        sa.CheckConstraint("transmitted_bytes >= 0", name="ck_analysis_tx_bytes"),
        sa.CheckConstraint("received_packets >= 0", name="ck_analysis_rx_packets"),
        sa.CheckConstraint("transmitted_packets >= 0", name="ck_analysis_tx_packets"),
        sa.PrimaryKeyConstraint("session_id"),
    )
    op.create_index(
        "ix_analysis_sessions_owner_time",
        "analysis_sessions",
        ["owner_hash", "received_at", "session_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_analysis_sessions_owner_time", table_name="analysis_sessions")
    op.drop_table("analysis_sessions")
