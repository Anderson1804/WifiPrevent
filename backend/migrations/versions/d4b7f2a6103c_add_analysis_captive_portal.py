"""add captive portal to analysis sessions

Revision ID: d4b7f2a6103c
Revises: c1a4e9d72b10
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "d4b7f2a6103c"
down_revision: Union[str, Sequence[str], None] = "c1a4e9d72b10"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "analysis_sessions",
        sa.Column("captive_portal", sa.Boolean(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("analysis_sessions", "captive_portal")
