"""add protocol metadata

Revision ID: 33a284743184
Revises: 858cfe84a3d1
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "33a284743184"
down_revision: Union[str, Sequence[str], None] = "858cfe84a3d1"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


FIELDS = (
    "parsed_packets",
    "unparsed_packets",
    "ipv4_packets",
    "ipv6_packets",
    "tcp_packets",
    "udp_packets",
    "icmp_packets",
    "other_transport_packets",
    "dns_packets",
    "http_packets",
    "tls_or_quic_packets",
    "unique_destinations",
)


def upgrade() -> None:
    for field in FIELDS:
        op.add_column(
            "analysis_sessions",
            sa.Column(field, sa.BigInteger(), nullable=False, server_default="0"),
        )
    op.create_check_constraint(
        "ck_analysis_metadata_nonnegative",
        "analysis_sessions",
        " AND ".join(f"{field} >= 0" for field in FIELDS),
    )


def downgrade() -> None:
    op.drop_constraint("ck_analysis_metadata_nonnegative", "analysis_sessions", type_="check")
    for field in reversed(FIELDS):
        op.drop_column("analysis_sessions", field)
