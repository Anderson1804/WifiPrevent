"""Create an unreviewed run from receiver evidence and an app export."""
import json
import re
from datetime import datetime
from pathlib import Path

from app.schemas.temporal_capture import TemporalCapture

SCENARIO_TYPES = {"S04": "connection_burst", "S06": "connection_burst", "S08": "connection_burst",
                  "S05": "periodic_connections", "S07": "periodic_connections", "S03": "normal"}


def prepare_run(capture_path: Path, receiver_path: Path, execution_id: str, scenario: str, pair: str):
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", execution_id) or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", pair):
        raise ValueError("Usar códigos anónimos alfanuméricos para la ejecución y el par.")
    if scenario not in SCENARIO_TYPES:
        raise ValueError("Este preparador admite S03, S04, S05, S06, S07 y S08.")
    document = json.loads(capture_path.read_text(encoding="utf-8-sig"))
    capture = TemporalCapture.model_validate(document["temporal_capture"])
    start = datetime.fromisoformat(capture.started_at_utc.replace("Z", "+00:00"))
    records = [json.loads(line) for line in receiver_path.read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    selected = [r for r in records if r.get("execution_id") == execution_id]
    if not selected:
        raise ValueError("El receptor no tiene evidencia de esta ejecución.")
    offsets = [int((datetime.fromisoformat(r["received_at_utc"].replace("Z", "+00:00"))-start).total_seconds()*1000) for r in selected]
    if any(t < 0 or t > capture.elapsed_ms for t in offsets):
        raise ValueError("La evidencia está fuera de la captura; revisar relojes, código y punto de observación.")
    normal = scenario not in {"S06", "S07"}
    return dict(execution_id=execution_id, scenario_code=scenario, comparison_pair_id=pair,
        phase="postest", mechanism="wifiprevent", date_utc=start.date().isoformat(),
        valid_for_evaluation=False, evidence_code=f"receiver-{execution_id}",
        received_actions=len(selected), review_status="pending_independent_review",
        references=[dict(id="reference-1", event_type=SCENARIO_TYPES[scenario], start_ms=min(offsets), end_ms=max(offsets),
            reference_status="normal_control" if normal else "candidate_simulation", risk_level=None, reviewed=False)],
        detections=[dict(id=e["event_id"], event_type=e["event_type"], start_ms=e["start_ms"], end_ms=e["end_ms"],
            risk_level=e["risk_level"], method=e["method"]) for e in document.get("detected_events", [])],
        notes="La evidencia prueba recepción, no la intención ni el nivel de riesgo. Referencias pendientes.")
