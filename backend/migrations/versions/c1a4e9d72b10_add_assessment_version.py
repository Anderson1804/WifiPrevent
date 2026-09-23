"""add assessment methodology version

Revision ID: c1a4e9d72b10
Revises: 0299d20df7a6
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "c1a4e9d72b10"
down_revision: Union[str, Sequence[str], None] = "0299d20df7a6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "analysis_sessions",
        sa.Column("assessment_version", sa.String(length=32), nullable=True),
    )
    op.execute(
        "UPDATE analysis_sessions SET assessment_version = 'legacy' "
        "WHERE risk_level IS NOT NULL"
    )


def downgrade() -> None:
    op.drop_column("analysis_sessions", "assessment_version")
