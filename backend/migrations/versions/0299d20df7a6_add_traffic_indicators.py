"""add traffic indicators

Revision ID: 0299d20df7a6
Revises: aa697ff19d0c
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "0299d20df7a6"
down_revision: Union[str, Sequence[str], None] = "aa697ff19d0c"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "analysis_sessions",
        sa.Column("capture_mode", sa.String(length=16), nullable=False, server_default="controlled"),
    )
    op.add_column(
        "analysis_sessions",
        sa.Column("indicators", sa.JSON(), nullable=False, server_default="[]"),
    )
    op.execute(
        """
        UPDATE analysis_sessions
        SET indicators = json_build_array(json_build_object(
            'code', 'legacy_session',
            'severity', 'info',
            'title', 'Sesión anterior',
            'description', 'La sesión fue creada antes de incorporar el motor de indicadores.'
        ))
        """
    )


def downgrade() -> None:
    op.drop_column("analysis_sessions", "indicators")
    op.drop_column("analysis_sessions", "capture_mode")
