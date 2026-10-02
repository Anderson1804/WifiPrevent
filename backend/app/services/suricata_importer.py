"""Import explicitly mapped EVE alert signatures, without carrying PII to reports."""
import json
from datetime import datetime, timezone
from pathlib import Path


def import_suricata(path: Path, mapping_path: Path, started_at_utc: str):
    mapping = json.loads(mapping_path.read_text(encoding="utf-8"))
    if not mapping:
        raise ValueError("Definir firmas y equivalencias; no importar todas las alertas como amenazas.")
    start = datetime.fromisoformat(started_at_utc.replace("Z", "+00:00"))
    if start.tzinfo is None or start.utcoffset() != timezone.utc.utcoffset(start):
        raise ValueError("El inicio debe expresarse en UTC.")
    output = []
    with path.open(encoding="utf-8-sig") as handle:
        for line in handle:
            if len(line) > 1_000_000:
                raise ValueError("Línea EVE demasiado grande.")
            event = json.loads(line)
            if event.get("event_type") != "alert":
                continue
            rule = mapping.get(str(event["alert"]["signature_id"]))
            if rule is None:
                continue
            if rule.get("event_type") not in {"connection_burst", "periodic_connections", "repeated_failures"}:
                raise ValueError("La firma no tiene una equivalencia de evento admitida.")
            # Severity is interpreted by the protocol; never assume severity 1 == risk high.
            risk = rule.get("severity_to_risk", {}).get(str(event["alert"].get("severity")), "unknown")
            if risk not in {"low", "medium", "high", "unknown"}:
                raise ValueError("Equivalencia de riesgo no válida.")
            timestamp = datetime.fromisoformat(event["timestamp"].replace("Z", "+00:00"))
            offset = int((timestamp - start).total_seconds() * 1000)
            if offset < 0:
                continue
            output.append(dict(id=f"suricata-{len(output)+1}", event_type=rule["event_type"],
                start_ms=offset, end_ms=offset, risk_level=risk, method="suricata-eve-mapped"))
            if len(output) > 10_000:
                raise ValueError("Separar la captura en ejecuciones acotadas.")
    return output
