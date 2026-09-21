"""update analysis scope wording

Revision ID: aa697ff19d0c
Revises: 33a284743184
"""
from typing import Sequence, Union

from alembic import op


revision: str = "aa697ff19d0c"
down_revision: Union[str, Sequence[str], None] = "33a284743184"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


OLD_REASON = (
    "La sesión reunió una muestra de volumen, pero todavía no clasifica "
    "protocolos, destinos ni contenido."
)
NEW_REASON = (
    "La sesión clasificó una muestra controlada de protocolos y destinos; "
    "todavía no representa todo el tráfico ni inspecciona contenido."
)


def upgrade() -> None:
    op.execute(
        f"""
        UPDATE analysis_sessions
        SET risk_reasons = (
            SELECT json_agg(
                CASE WHEN reason = '{OLD_REASON}' THEN '{NEW_REASON}' ELSE reason END
            )
            FROM json_array_elements_text(risk_reasons) AS entries(reason)
        )
        WHERE risk_reasons IS NOT NULL
        """
    )


def downgrade() -> None:
    op.execute(
        f"""
        UPDATE analysis_sessions
        SET risk_reasons = (
            SELECT json_agg(
                CASE WHEN reason = '{NEW_REASON}' THEN '{OLD_REASON}' ELSE reason END
            )
            FROM json_array_elements_text(risk_reasons) AS entries(reason)
        )
        WHERE risk_reasons IS NOT NULL
        """
    )
