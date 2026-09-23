"""add relay observations

Revision ID: f7a21c093bd4
Revises: d4b7f2a6103c
"""
from alembic import op
import sqlalchemy as sa


revision = "f7a21c093bd4"
down_revision = "d4b7f2a6103c"
branch_labels = None
depends_on = None


FIELDS = (
    "relay_tcp_connections",
    "relay_udp_datagrams",
    "relay_dns_observations",
    "relay_http_observations",
    "relay_tls_or_quic_observations",
    "relay_other_observations",
    "relay_unique_destinations",
)


def upgrade() -> None:
    op.add_column(
        "analysis_sessions",
        sa.Column("relay_metrics_collected", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    for field in FIELDS:
        op.add_column(
            "analysis_sessions",
            sa.Column(field, sa.BigInteger(), nullable=False, server_default="0"),
        )
    op.create_check_constraint(
        "ck_analysis_relay_metadata_nonnegative",
        "analysis_sessions",
        " AND ".join(f"{field} >= 0" for field in FIELDS),
    )


def downgrade() -> None:
    op.drop_constraint(
        "ck_analysis_relay_metadata_nonnegative", "analysis_sessions", type_="check"
    )
    for field in reversed(FIELDS):
        op.drop_column("analysis_sessions", field)
    op.drop_column("analysis_sessions", "relay_metrics_collected")
