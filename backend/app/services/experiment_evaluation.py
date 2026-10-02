"""Pair detections with independent references and export Annex 2 honestly."""
import csv
import json
import re
from datetime import date
from pathlib import Path


ANNEX_COLUMNS = ("execution_id", "scenario_code", "comparison_pair_id", "phase", "mechanism", "date_utc",
    "total_reference_threats", "true_detected_threats", "missed_threats", "false_alarms", "duplicate_alerts",
    "detection_percentage", "events_with_valid_risk_reference", "events_with_correct_risk",
    "risk_classification_percentage", "evaluation_status", "evidence_code")
KINDS = {"connection_burst", "periodic_connections", "repeated_failures", "normal"}


def evaluate_experiment(book: dict):
    if book.get("schema_version") != 1 or book.get("data_kind") not in {"pilot", "study"}:
        raise ValueError("El manifiesto necesita schema_version 1 y data_kind pilot o study.")
    tolerance = book.get("matching_tolerance_ms")
    if type(tolerance) is not int or not 0 <= tolerance <= 10_000:
        raise ValueError("Fijar una tolerancia de emparejamiento entre 0 y 10000 ms.")
    if not book.get("risk_rubric_version"):
        raise ValueError("Falta la versión de la rúbrica de riesgo independiente.")
    results, details, seen = [], [], set()
    signatures = {}
    for run in book.get("executions", []):
        for field in ("execution_id", "scenario_code"):
            if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", run.get(field, "")):
                raise ValueError("Usar códigos de ejecución y escenario anónimos; no fórmulas CSV.")
        for field in ("comparison_pair_id", "evidence_code"):
            if run.get(field) and not re.fullmatch(r"[A-Za-z0-9_-]{1,96}", run[field]):
                raise ValueError("Código de par o evidencia no válido.")
        date.fromisoformat(run["date_utc"])
        key = (run["execution_id"], run["mechanism"])
        if key in seen:
            raise ValueError("Ejecución y mecanismo duplicados.")
        seen.add(key)
        expected_phase = {"suricata": "pretest", "wifiprevent": "postest"}
        if run.get("mechanism") not in expected_phase or run.get("phase") != expected_phase.get(run.get("mechanism")):
            raise ValueError("El pretest usa Suricata y el postest WiFiPrevent.")
        refs, detections = run.get("references", []), run.get("detections", [])
        if len({r["id"] for r in refs}) != len(refs) or len({d["id"] for d in detections}) != len(detections):
            raise ValueError("Los identificadores de referencia y detección deben ser únicos.")
        for item in refs + detections:
            if item.get("event_type") not in KINDS:
                raise ValueError("Tipo de evento no incluido en el protocolo.")
            if any(type(item.get(k)) is not int or item[k] < 0 for k in ("start_ms", "end_ms")) or item["end_ms"] < item["start_ms"]:
                raise ValueError("Intervalo de evento no válido.")
        if any(d["event_type"] == "normal" for d in detections):
            raise ValueError("Los controles normales sin alerta se registran en risk_assignments.")
        signatures[key] = sorted((r["id"], r["event_type"], r.get("reference_status"), r.get("risk_level")) for r in refs)
        for item in refs + detections:
            if item.get("risk_level") not in {None, "low", "medium", "high", "unknown"}:
                raise ValueError("Nivel de riesgo no válido.")
        valid = run.get("valid_for_evaluation") is True and all(r.get("reviewed") is True for r in refs)
        if any(r.get("reference_status") not in {"normal_control", "confirmed_test_threat", "candidate_simulation"} for r in refs):
            raise ValueError("Estado de referencia no válido.")
        if book["data_kind"] == "study" and any(r["reference_status"] == "candidate_simulation" for r in refs):
            valid = False
        used, assigned, tp, false, duplicates = set(), {}, 0, 0, 0
        positives = {r["id"] for r in refs if r["reference_status"] == "confirmed_test_threat"}
        for detection in sorted(detections, key=lambda d: (d["start_ms"], d["id"])):
            matches = [r for r in refs if r["event_type"] == detection["event_type"] and
                detection["start_ms"] <= r["end_ms"] + tolerance and detection["end_ms"] >= r["start_ms"] - tolerance]
            available = [r for r in matches if r["id"] not in used]
            match = min(available, key=lambda r: abs(r["start_ms"] - detection["start_ms"])) if available else None
            if match:
                used.add(match["id"])
                assigned[match["id"]] = detection.get("risk_level", "unknown")
                if match["id"] in positives:
                    tp += 1
                    state = "true_positive"
                elif match["reference_status"] == "candidate_simulation":
                    state = "candidate_match"
                else:
                    false += 1
                    state = "false_alarm"
            elif matches:
                duplicates += 1
                state = "duplicate"
            else:
                false += 1
                state = "false_alarm"
            details.append(dict(execution_id=run["execution_id"], mechanism=run["mechanism"],
                detection_id=detection["id"], reference_event_id=match["id"] if match else None, matching_status=state))
        # Explicit reference-linked risk assessments can include normal windows, which emit no alert.
        for score in run.get("risk_assignments", []):
            ref_id = score["reference_event_id"]
            if ref_id not in {r["id"] for r in refs} or ref_id in assigned:
                raise ValueError("Asignación de riesgo sin referencia o duplicada.")
            if score.get("risk_level") not in {"low", "medium", "high", "unknown"}:
                raise ValueError("Asignación de riesgo no válida.")
            assigned[ref_id] = score["risk_level"]
        risk_refs = [r for r in refs if r.get("risk_level") in {"low", "medium", "high"}]
        correct = sum(assigned.get(r["id"]) == r["risk_level"] for r in risk_refs)
        total = len(positives)
        results.append(dict(execution_id=run["execution_id"], scenario_code=run["scenario_code"],
            comparison_pair_id=run.get("comparison_pair_id", ""), phase=run["phase"], mechanism=run["mechanism"],
            date_utc=run["date_utc"], total_reference_threats=total, true_detected_threats=tp,
            missed_threats=total-tp, false_alarms=false, duplicate_alerts=duplicates,
            detection_percentage=round(100*tp/total, 4) if total and valid else None,
            events_with_valid_risk_reference=len(risk_refs), events_with_correct_risk=correct,
            risk_classification_percentage=round(100*correct/len(risk_refs), 4) if risk_refs and valid else None,
            evaluation_status="excluded" if not valid else book["data_kind"], evidence_code=run.get("evidence_code", "")))
    if not results:
        raise ValueError("El manifiesto no contiene ejecuciones; no se inventarán resultados.")
    comparisons = []
    for pair in sorted({r["comparison_pair_id"] for r in results if r["comparison_pair_id"]}):
        candidates = [r for r in results if r["comparison_pair_id"] == pair and r["evaluation_status"] != "excluded"]
        pre = [r for r in candidates if r["phase"] == "pretest"]
        post = [r for r in candidates if r["phase"] == "postest"]
        comparable = (len(pre) == len(post) == 1 and pre[0]["scenario_code"] == post[0]["scenario_code"]
                      and pre[0]["total_reference_threats"] == post[0]["total_reference_threats"]
                      and signatures[(pre[0]["execution_id"], pre[0]["mechanism"])] ==
                          signatures[(post[0]["execution_id"], post[0]["mechanism"])])
        delta = None
        if comparable and pre[0]["detection_percentage"] is not None and post[0]["detection_percentage"] is not None:
            delta = post[0]["detection_percentage"] - pre[0]["detection_percentage"]
        risk_delta = None
        if comparable and pre[0]["risk_classification_percentage"] is not None and post[0]["risk_classification_percentage"] is not None:
            risk_delta = post[0]["risk_classification_percentage"] - pre[0]["risk_classification_percentage"]
        comparisons.append(dict(comparison_pair_id=pair, comparable=comparable,
            detection_difference_percentage_points=delta, risk_difference_percentage_points=risk_delta))
    from app.services.paired_statistics import paired_statistics
    return dict(data_kind=book["data_kind"], results=results, matches=details, comparisons=comparisons,
                statistics=paired_statistics(book, comparisons),
                interpretation="No implica significancia estadística ni detección validada fuera de estos escenarios.")


def write_report(book_path: Path, output: Path):
    report = evaluate_experiment(json.loads(book_path.read_text(encoding="utf-8-sig")))
    output.mkdir(parents=True, exist_ok=True)
    with (output / "anexo2.csv").open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=ANNEX_COLUMNS)
        writer.writeheader(); writer.writerows(report["results"])
    (output / "evaluacion.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report
