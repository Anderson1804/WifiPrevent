"""Preserve event and risk predictions including normal windows."""
from alembic import op
import sqlalchemy as sa

revision = "c094bba76add"
down_revision = "b830e14206aa"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("analysis_sessions", sa.Column("window_assessments", sa.JSON(), nullable=False, server_default="[]"))


def downgrade():
    op.drop_column("analysis_sessions", "window_assessments")
