from datetime import datetime, timezone
from typing import Annotated, Literal
from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, Response, status
from sqlalchemy import func, select, tuple_
from sqlalchemy.dialects.postgresql import insert

from app.api.dependencies import DatabaseSession
from app.core import OwnerHash
from app.db.models.analysis_session import AnalysisSessionRecord
from app.schemas.analysis_session import (
    AnalysisHistorySummary,
    AnalysisSessionItem,
    AnalysisSessionPage,
    AnalysisSessionReading,
    AnalysisSessionReceipt,
)
from app.services import evaluate_analysis_risk, evaluate_traffic_indicators


router = APIRouter(prefix="/api/v1/analysis-sessions", tags=["analysis-sessions"])


@router.delete("/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_analysis_session(
        session_id: UUID,
        session: DatabaseSession,
        owner: OwnerHash,
) -> Response:
    record = session.scalar(
        select(AnalysisSessionRecord).where(
            AnalysisSessionRecord.session_id == session_id,
            AnalysisSessionRecord.owner_hash == owner,
        )
    )
    if record is None:
        raise HTTPException(status_code=404, detail="Sesión no encontrada.")
    session.delete(record)
    session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/summary", response_model=AnalysisHistorySummary)
def analysis_history_summary(
        session: DatabaseSession,
        owner: OwnerHash,
) -> AnalysisHistorySummary:
    row = session.execute(
        select(
            func.count().label("total_sessions"),
            func.count().filter(AnalysisSessionRecord.risk_level == "low").label("low_risk"),
            func.count().filter(AnalysisSessionRecord.risk_level == "medium").label("medium_risk"),
            func.count().filter(AnalysisSessionRecord.risk_level == "high").label("high_risk"),
            func.count().filter(AnalysisSessionRecord.risk_level == "unknown").label("unknown_risk"),
            func.count().filter(AnalysisSessionRecord.risk_level.is_(None)).label("not_evaluated"),
            func.count().filter(
                AnalysisSessionRecord.capture_mode == "controlled"
            ).label("controlled_sessions"),
            func.count().filter(
                AnalysisSessionRecord.capture_mode == "full"
            ).label("full_sessions"),
        ).where(AnalysisSessionRecord.owner_hash == owner)
    ).one()
    return AnalysisHistorySummary(**row._mapping)


@router.post("", response_model=AnalysisSessionReceipt)
def save_analysis_session(
        reading: AnalysisSessionReading,
        session: DatabaseSession,
        owner: OwnerHash,
) -> AnalysisSessionReceipt:
    values = reading.model_dump()
    assessment = evaluate_analysis_risk(
        security_type=reading.security_type,
        capture_mode=reading.capture_mode,
        duration_seconds=reading.duration_seconds,
        received_bytes=reading.received_bytes,
        transmitted_bytes=reading.transmitted_bytes,
        received_packets=reading.received_packets,
        transmitted_packets=reading.transmitted_packets,
    )
    indicators = evaluate_traffic_indicators(
        capture_mode=reading.capture_mode,
        duration_seconds=reading.duration_seconds,
        received_bytes=reading.received_bytes,
        transmitted_bytes=reading.transmitted_bytes,
        received_packets=reading.received_packets,
        transmitted_packets=reading.transmitted_packets,
        parsed_packets=reading.parsed_packets,
        unparsed_packets=reading.unparsed_packets,
        ipv4_packets=reading.ipv4_packets,
        ipv6_packets=reading.ipv6_packets,
        http_packets=reading.http_packets,
    )
    statement = (
        insert(AnalysisSessionRecord)
        .values(
            owner_hash=owner,
            received_at=datetime.now(timezone.utc),
            **values,
            risk_level=assessment.level,
            risk_reasons=list(assessment.reasons),
            assessment_scope=(
                "connection_and_traffic_metadata"
                if reading.capture_mode == "full"
                else "connection_metadata"
            ),
            traffic_analysis_performed=reading.capture_mode == "full",
            indicators=[indicator.__dict__ for indicator in indicators],
        )
        .on_conflict_do_nothing(index_elements=[AnalysisSessionRecord.session_id])
    )
    session.execute(statement)
    row = session.scalar(
        select(AnalysisSessionRecord).where(
            AnalysisSessionRecord.session_id == reading.session_id,
        )
    )

    expected = reading.model_dump(exclude={"session_id"})
    if row is None:
        session.rollback()
        raise HTTPException(status_code=500, detail="No fue posible recuperar la sesión.")
    if row.owner_hash != owner or any(getattr(row, key) != value for key, value in expected.items()):
        session.rollback()
        raise HTTPException(
            status_code=409,
            detail="El identificador de sesión ya corresponde a otros datos.",
        )

    session.commit()
    return AnalysisSessionReceipt(
        session_id=row.session_id,
        received_at=row.received_at,
        risk_level=row.risk_level,
        risk_reasons=row.risk_reasons,
        assessment_scope=row.assessment_scope,
        traffic_analysis_performed=row.traffic_analysis_performed,
        capture_mode=row.capture_mode,
        indicators=row.indicators,
    )


@router.get("", response_model=AnalysisSessionPage)
def analysis_history(
        session: DatabaseSession,
        owner: OwnerHash,
        limit: Annotated[int, Query(ge=1, le=100)] = 20,
        before: UUID | None = None,
        risk_level: Literal["low", "medium", "high", "unknown"] | None = None,
        capture_mode: Literal["controlled", "full"] | None = None,
) -> AnalysisSessionPage:
    statement = select(AnalysisSessionRecord).where(AnalysisSessionRecord.owner_hash == owner)
    if risk_level is not None:
        statement = statement.where(AnalysisSessionRecord.risk_level == risk_level)
    if capture_mode is not None:
        statement = statement.where(AnalysisSessionRecord.capture_mode == capture_mode)
    if before is not None:
        cursor_statement = select(AnalysisSessionRecord).where(
            AnalysisSessionRecord.owner_hash == owner,
            AnalysisSessionRecord.session_id == before,
        )
        if risk_level is not None:
            cursor_statement = cursor_statement.where(
                AnalysisSessionRecord.risk_level == risk_level
            )
        if capture_mode is not None:
            cursor_statement = cursor_statement.where(
                AnalysisSessionRecord.capture_mode == capture_mode
            )
        cursor = session.scalar(cursor_statement)
        if cursor is None:
            raise HTTPException(status_code=404, detail="Sesión no encontrada.")
        statement = statement.where(
            tuple_(AnalysisSessionRecord.received_at, AnalysisSessionRecord.session_id)
            < tuple_(cursor.received_at, cursor.session_id)
        )

    rows = session.scalars(
        statement.order_by(
            AnalysisSessionRecord.received_at.desc(),
            AnalysisSessionRecord.session_id.desc(),
        ).limit(limit + 1)
    ).all()
    return AnalysisSessionPage(
        items=[AnalysisSessionItem.model_validate(row) for row in rows[:limit]],
        next_before=rows[limit - 1].session_id if len(rows) > limit else None,
    )
