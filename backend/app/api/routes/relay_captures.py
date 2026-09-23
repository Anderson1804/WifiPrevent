from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.core import OwnerHash
from app.schemas.relay_capture import RelayCaptureMetrics, RelayCaptureReady
from app.services.relay_metrics_client import (
    RelayMetricsUnavailable,
    read_relay_session,
    start_relay_session,
)


router = APIRouter(prefix="/api/v1/relay-captures", tags=["relay-captures"])


@router.post("/{session_id}/start", response_model=RelayCaptureReady)
def start_capture_metrics(session_id: UUID, owner: OwnerHash) -> RelayCaptureReady:
    del owner
    try:
        return RelayCaptureReady.model_validate(start_relay_session(session_id))
    except RelayMetricsUnavailable as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@router.get("/{session_id}", response_model=RelayCaptureMetrics)
def capture_metrics(session_id: UUID, owner: OwnerHash) -> RelayCaptureMetrics:
    del owner
    try:
        return RelayCaptureMetrics.model_validate(read_relay_session(session_id))
    except RelayMetricsUnavailable as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
