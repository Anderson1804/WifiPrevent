from datetime import datetime, timezone
from typing import Annotated
from uuid import UUID, uuid4

from fastapi import APIRouter, Header, HTTPException, Query
from sqlalchemy import select, tuple_
from sqlalchemy.dialects.postgresql import insert

from app.api.dependencies import DatabaseSession
from app.core import OwnerHash
from app.db.models.connection_check import ConnectionCheck
from app.schemas import (
    ConnectionReading,
    HistoryItem,
    HistoryPage,
    Receipt,
)
from app.services import evaluate_risk


router = APIRouter(
    prefix="/api/v1/connection-checks",
    tags=["connection-checks"],
)


@router.post("", response_model=Receipt)
def receive_connection(
        reading: ConnectionReading,
        session: DatabaseSession,
        owner: OwnerHash,
        x_request_id: Annotated[UUID, Header()],
) -> Receipt:
    assessment = evaluate_risk(
        security_type=reading.security_type,
        captive_portal=reading.captive_portal,
    )
    values = reading.model_dump()

    statement = (
        insert(ConnectionCheck)
        .values(
            receipt_id=uuid4(),
            owner_hash=owner,
            request_id=x_request_id,
            received_at=datetime.now(timezone.utc),
            **values,
            risk_level=assessment.level,
            risk_reasons=list(assessment.reasons),
            analysis_performed=True,
        )
        .on_conflict_do_nothing(
            constraint="uq_checks_owner_request",
        )
    )

    session.execute(statement)

    row = session.scalar(
        select(ConnectionCheck).where(
            ConnectionCheck.owner_hash == owner,
            ConnectionCheck.request_id == x_request_id,
            )
    )

    if row is None:
        session.rollback()
        raise HTTPException(
            status_code=500,
            detail="No fue posible recuperar el registro.",
        )

    if any(getattr(row, key) != value for key, value in values.items()):
        session.rollback()
        raise HTTPException(
            status_code=409,
            detail="El identificador de envío ya corresponde a otros datos.",
        )

    session.commit()

    return Receipt(
        receipt_id=row.receipt_id,
        received_at=row.received_at,
        risk_level=row.risk_level,
        risk_reasons=row.risk_reasons,
        analysis_performed=row.analysis_performed,
        message=(
            "La conexión se guardó y su riesgo fue evaluado."
            if row.analysis_performed
            else "Los datos se guardaron. Riesgo no evaluado."
        ),
    )


@router.get("", response_model=HistoryPage)
def history(
        session: DatabaseSession,
        owner: OwnerHash,
        limit: Annotated[int, Query(ge=1, le=100)] = 20,
        before: UUID | None = None,
) -> HistoryPage:
    statement = select(ConnectionCheck).where(
        ConnectionCheck.owner_hash == owner,
        )

    if before is not None:
        cursor = session.scalar(
            select(ConnectionCheck).where(
                ConnectionCheck.owner_hash == owner,
                ConnectionCheck.receipt_id == before,
                )
        )

        if cursor is None:
            raise HTTPException(
                status_code=404,
                detail="Registro no encontrado.",
            )

        statement = statement.where(
            tuple_(
                ConnectionCheck.received_at,
                ConnectionCheck.receipt_id,
            )
            < tuple_(
                cursor.received_at,
                cursor.receipt_id,
            )
        )

    rows = session.scalars(
        statement
        .order_by(
            ConnectionCheck.received_at.desc(),
            ConnectionCheck.receipt_id.desc(),
        )
        .limit(limit + 1)
    ).all()

    return HistoryPage(
        items=[
            HistoryItem.model_validate(row)
            for row in rows[:limit]
        ],
        next_before=(
            rows[limit - 1].receipt_id
            if len(rows) > limit
            else None
        ),
    )