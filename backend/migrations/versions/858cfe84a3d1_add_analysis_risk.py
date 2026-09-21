"""add preliminary analysis risk

Revision ID: 858cfe84a3d1
Revises: 75b83592a660
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "858cfe84a3d1"
down_revision: Union[str, Sequence[str], None] = "75b83592a660"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("analysis_sessions", sa.Column("risk_level", sa.String(length=32)))
    op.add_column("analysis_sessions", sa.Column("risk_reasons", sa.JSON()))
    op.add_column("analysis_sessions", sa.Column("assessment_scope", sa.String(length=32)))
    op.add_column(
        "analysis_sessions",
        sa.Column("traffic_analysis_performed", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.execute(
        """
        UPDATE analysis_sessions
        SET risk_level = CASE
                WHEN duration_seconds < 5 OR received_packets + transmitted_packets < 10 THEN 'unknown'
                WHEN security_type IN ('OPEN', 'WEP') THEN 'high'
                WHEN security_type IN ('WPA_WPA2_PSK', 'OWE') THEN 'medium'
                WHEN security_type IN ('WPA_WPA2_ENTERPRISE', 'WPA3_SAE') THEN 'low'
                ELSE 'unknown'
            END,
            risk_reasons = CASE
                WHEN duration_seconds < 5 OR received_packets + transmitted_packets < 10 THEN
                    json_build_array(
                        'La sesión no reunió una muestra mínima de cinco segundos y diez paquetes.',
                        'La evaluación se limita a metadatos y no inspecciona el contenido del tráfico.'
                    )
                ELSE json_build_array(
                    CASE
                        WHEN security_type = 'OPEN' THEN 'La red no informa un mecanismo de cifrado.'
                        WHEN security_type = 'WEP' THEN 'WEP utiliza un mecanismo de seguridad obsoleto.'
                        WHEN security_type = 'WPA_WPA2_PSK' THEN 'Android informa seguridad PSK, pero no permite distinguir con precisión entre WPA y WPA2.'
                        WHEN security_type = 'WPA_WPA2_ENTERPRISE' THEN 'La red utiliza autenticación empresarial.'
                        WHEN security_type = 'WPA3_SAE' THEN 'La red utiliza WPA3 con autenticación SAE.'
                        WHEN security_type = 'OWE' THEN 'OWE cifra la conexión, pero no autentica la identidad de la red.'
                        ELSE 'No existe información suficiente sobre el tipo de seguridad.'
                    END,
                    'La sesión reunió una muestra de volumen, pero todavía no clasifica protocolos, destinos ni contenido.'
                )
            END,
            assessment_scope = 'connection_metadata'
        """
    )


def downgrade() -> None:
    op.drop_column("analysis_sessions", "traffic_analysis_performed")
    op.drop_column("analysis_sessions", "assessment_scope")
    op.drop_column("analysis_sessions", "risk_reasons")
    op.drop_column("analysis_sessions", "risk_level")
