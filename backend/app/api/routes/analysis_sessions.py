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
    AnalysisTrainingObservation,
)
from app.services.ml_risk_classifier import FEATURE_NAMES
from app.services.training_observation_exporter import export_training_observation
from app.services import (
    ASSESSMENT_VERSION,
    evaluate_analysis_risk,
    evaluate_sample_quality,
    evaluate_traffic_indicators,
    recommend_preventive_actions,
)


router = APIRouter(prefix="/api/v1/analysis-sessions", tags=["analysis-sessions"])


@router.get("/{session_id}/training-observation", response_model=AnalysisTrainingObservation)
def training_observation(
        session_id: UUID,
        session: DatabaseSession,
        owner: OwnerHash,
        response: Response,
) -> AnalysisTrainingObservation:
    record = session.scalar(
        select(AnalysisSessionRecord).where(
            AnalysisSessionRecord.session_id == session_id,
            AnalysisSessionRecord.owner_hash == owner,
        )
    )
    if record is None:
        raise HTTPException(status_code=404, detail="Sesión no encontrada.")
    try:
        csv_content = export_training_observation({
            name: getattr(record, name) for name in FEATURE_NAMES
        })
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    response.headers["Cache-Control"] = "no-store"
    return AnalysisTrainingObservation(csv_content=csv_content)


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
            func.count().filter(
                AnalysisSessionRecord.relay_metrics_collected.is_(True)
            ).label("relay_sessions"),
            func.coalesce(func.sum(AnalysisSessionRecord.relay_tcp_connections), 0).label(
                "relay_tcp_connections"
            ),
            func.coalesce(func.sum(AnalysisSessionRecord.relay_udp_datagrams), 0).label(
                "relay_udp_datagrams"
            ),
            func.coalesce(func.sum(AnalysisSessionRecord.relay_dns_observations), 0).label(
                "relay_dns_observations"
            ),
            func.coalesce(func.sum(AnalysisSessionRecord.relay_http_observations), 0).label(
                "relay_http_observations"
            ),
            func.coalesce(
                func.sum(AnalysisSessionRecord.relay_tls_or_quic_observations), 0
            ).label("relay_tls_or_quic_observations"),
            func.coalesce(func.sum(AnalysisSessionRecord.relay_other_observations), 0).label(
                "relay_other_observations"
            ),
            func.coalesce(func.sum(AnalysisSessionRecord.relay_unique_destinations), 0).label(
                "relay_unique_destinations"
            ),
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
        captive_portal=reading.captive_portal,
        capture_mode=reading.capture_mode,
        duration_seconds=reading.duration_seconds,
        received_bytes=reading.received_bytes,
        transmitted_bytes=reading.transmitted_bytes,
        received_packets=reading.received_packets,
        transmitted_packets=reading.transmitted_packets,
        ml_features=values,
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
        http_packets=reading.http_packets + reading.relay_http_observations,
    )
    sample_quality = evaluate_sample_quality(
        reading.duration_seconds,
        reading.received_packets,
        reading.transmitted_packets,
    )
    recommendations = recommend_preventive_actions(
        assessment.level,
        reading.security_type,
        reading.captive_portal,
        sample_quality,
        (indicator.code for indicator in indicators),
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
            assessment_version=(
                assessment.assessment_version
                or (
                    ASSESSMENT_VERSION
                    if reading.relay_metrics_collected
                    else "rules-aggregate-v2"
                )
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
        assessment_version=row.assessment_version,
        traffic_analysis_performed=row.traffic_analysis_performed,
        capture_mode=row.capture_mode,
        indicators=row.indicators,
        sample_quality=sample_quality,
        recommendations=list(recommendations),
        relay_metrics_collected=row.relay_metrics_collected,
        relay_tcp_connections=row.relay_tcp_connections,
        relay_udp_datagrams=row.relay_udp_datagrams,
        relay_dns_observations=row.relay_dns_observations,
        relay_http_observations=row.relay_http_observations,
        relay_tls_or_quic_observations=row.relay_tls_or_quic_observations,
        relay_other_observations=row.relay_other_observations,
        relay_unique_destinations=row.relay_unique_destinations,
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
        items=[
            _analysis_item(row)
            for row in rows[:limit]
        ],
        next_before=rows[limit - 1].session_id if len(rows) > limit else None,
    )


def _analysis_item(row: AnalysisSessionRecord) -> AnalysisSessionItem:
    sample_quality = evaluate_sample_quality(
        row.duration_seconds,
        row.received_packets,
        row.transmitted_packets,
    )
    indicators = row.indicators or []
    recommendations = recommend_preventive_actions(
        row.risk_level,
        row.security_type,
        row.captive_portal,
        sample_quality,
        (indicator.get("code", "") for indicator in indicators),
    )
    return AnalysisSessionItem.model_validate(row).model_copy(update={
        "sample_quality": sample_quality,
        "recommendations": list(recommendations),
    })
