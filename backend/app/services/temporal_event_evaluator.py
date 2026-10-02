"""Experimental pattern detection, deliberately separate from threat attribution."""
import csv
from collections import defaultdict
from io import StringIO
from statistics import mean, pstdev

from app.schemas.temporal_capture import ConnectionObservation, DetectedEvent, TemporalCapture

FEATURES = (
    "observation_count", "duration_ms", "mean_gap_ms", "gap_cv",
    "failure_ratio", "destination_count", "tcp_ratio", "http_ratio",
)
DATASET_COLUMNS = ("window_start_ms", "window_end_ms", "group_id", "evaluation_split", "reference_event_type",
                   "reference_risk_level", "reference_reviewed", *FEATURES)
METHOD = "patterns-pilot-v1"


def event_features(rows: list[ConnectionObservation]) -> dict[str, float]:
    gaps = [b.offset_ms - a.offset_ms for a, b in zip(rows, rows[1:])]
    average = mean(gaps) if gaps else 0
    n = len(rows)
    return dict(zip(FEATURES, (
        float(n), float(rows[-1].offset_ms - rows[0].offset_ms) if n else 0.,
        float(average), float(pstdev(gaps) / average) if average > 0 else 0.,
        sum(not r.success for r in rows) / n if n else 0.,
        float(len({r.destination_group for r in rows})),
        sum(r.transport == "tcp" for r in rows) / n if n else 0.,
        sum(r.category == "http" for r in rows) / n if n else 0.,
    ), strict=True))


def evaluate_temporal_events(capture: TemporalCapture | None) -> list[DetectedEvent]:
    if capture is None or capture.truncated:
        return []
    from app.services.event_model import predict_event
    modeled = []
    model_available = False
    for start in range(0, capture.elapsed_ms - 29_999, 30_000):
        rows = [r for r in capture.observations if start <= r.offset_ms < start + 30_000]
        prediction = predict_event(event_features(rows))
        if prediction is not None:
            model_available = True
            if prediction["event_type"] != "normal" and rows:
                modeled.append(_event(rows, prediction["event_type"], len(modeled),
                                      prediction["risk_level"], prediction["method"]))
    if model_available:
        return modeled
    groups: dict[int, list[ConnectionObservation]] = defaultdict(list)
    for row in capture.observations:
        if row.transport == "tcp":
            groups[row.destination_group].append(row)
    events: list[DetectedEvent] = []
    for rows in groups.values():
        successful = [r for r in rows if r.success]
        failures = [r for r in rows if not r.success]
        # Non-overlapping candidate episodes: one burst rather than one alert per connection.
        for subset, kind, minimum in (
            (successful, "connection_burst", 20),
            (failures, "repeated_failures", 10),
        ):
            left = 0
            while left < len(subset):
                right = left
                while right < len(subset) and subset[right].offset_ms - subset[left].offset_ms <= 10_000:
                    right += 1
                if right - left >= minimum:
                    events.append(_event(subset[left:right], kind, len(events)))
                    left = right
                else:
                    left += 1
        # Periodicity is visible but legitimate polling can be identical.
        chunk: list[ConnectionObservation] = []
        for row in successful:
            if chunk and not 4_000 <= row.offset_ms - chunk[-1].offset_ms <= 6_000:
                if len(chunk) >= 6:
                    events.append(_event(chunk, "periodic_connections", len(events)))
                chunk = []
            chunk.append(row)
        if len(chunk) >= 6:
            events.append(_event(chunk, "periodic_connections", len(events)))
    return sorted(events, key=lambda e: (e.start_ms, e.event_id))[:256]


def _event(rows, kind, index, risk="unknown", method=METHOD):
    features = event_features(rows)
    texts = {
        "connection_burst": "Ráfaga de conexiones; puede corresponder a actividad legítima.",
        "repeated_failures": "Intentos de conexión fallidos repetidos; no identifica su causa.",
        "periodic_connections": "Conexiones periódicas; no distingue sondeo legítimo de un beacon.",
    }
    return DetectedEvent(event_id=f"event-{index + 1}", event_type=kind,
                         start_ms=rows[0].offset_ms, end_ms=rows[-1].offset_ms,
                         observation_count=len(rows), risk_level=risk, method=method,
                         description=texts[kind], features=features)


def assess_temporal_windows(capture: TemporalCapture | None):
    from app.services.event_model import predict_event
    if capture is None or capture.truncated:
        return []
    assessments = []
    for start in range(0, capture.elapsed_ms - 29_999, 30_000):
        rows = [r for r in capture.observations if start <= r.offset_ms < start + 30_000]
        prediction = predict_event(event_features(rows))
        assessments.append(dict(window_start_ms=start, window_end_ms=start+30_000,
            event_type=prediction["event_type"] if prediction else "unclassified",
            risk_level=prediction["risk_level"] if prediction else "unknown",
            method=prediction["method"] if prediction else "no-trained-event-model"))
    return assessments


def export_event_observations(capture: TemporalCapture) -> str:
    if capture.truncated:
        raise ValueError("La serie temporal está incompleta; no es válida para exportar observaciones ML.")
    # Fixed 30 s units export normal windows too, instead of selecting only alerts.
    output = StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=DATASET_COLUMNS, lineterminator="\n")
    writer.writeheader()
    for start in range(0, capture.elapsed_ms, 30_000):
        if start + 30_000 > capture.elapsed_ms:
            break
        rows = [r for r in capture.observations if start <= r.offset_ms < start + 30_000]
        writer.writerow({"window_start_ms": start, "window_end_ms": start + 30_000, **event_features(rows)})
    if capture.elapsed_ms < 30_000:
        raise ValueError("La captura temporal necesita al menos 30 segundos.")
    return output.getvalue()
