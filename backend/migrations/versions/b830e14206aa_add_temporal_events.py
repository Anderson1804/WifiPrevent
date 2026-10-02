"""Add bounded connection timeline and observed pattern events."""
from alembic import op
import sqlalchemy as sa

revision = "b830e14206aa"
down_revision = "f7a21c093bd4"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("analysis_sessions", sa.Column("temporal_capture", sa.JSON(), nullable=True))
    op.add_column("analysis_sessions", sa.Column("detected_events", sa.JSON(), nullable=False, server_default="[]"))



def downgrade():
    op.drop_column("analysis_sessions", "detected_events")
    op.drop_column("analysis_sessions", "temporal_capture")
