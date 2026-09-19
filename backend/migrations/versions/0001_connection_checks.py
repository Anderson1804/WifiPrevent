"""Initial persisted connection readings."""
from alembic import op
import sqlalchemy as sa
revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

def upgrade():
    op.create_table("connection_checks",
        sa.Column("receipt_id", sa.Uuid(), primary_key=True),
        sa.Column("owner_hash", sa.String(64), nullable=False),
        sa.Column("request_id", sa.Uuid(), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ssid", sa.String(128)), sa.Column("rssi_dbm", sa.Integer()),
        sa.Column("frequency_mhz", sa.Integer()), sa.Column("link_speed_mbps", sa.Integer()),
        sa.Column("internet_validated", sa.Boolean(), nullable=False),
        sa.Column("captive_portal", sa.Boolean(), nullable=False),
        sa.Column("risk_level", sa.String(32)),
        sa.Column("analysis_performed", sa.Boolean(), nullable=False),
        sa.UniqueConstraint("owner_hash", "request_id", name="uq_checks_owner_request"),
        sa.CheckConstraint("rssi_dbm IS NULL OR rssi_dbm BETWEEN -126 AND -1", name="ck_checks_rssi"))
    op.create_index("ix_checks_owner_time", "connection_checks", ["owner_hash", "received_at", "receipt_id"])

def downgrade():
    op.drop_table("connection_checks")
