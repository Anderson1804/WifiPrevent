"""Independent event/risk heads with grouped evaluation and explicit activation."""
import csv
import hashlib
import json
import math
import os
import tempfile
import re
from pathlib import Path

from app.services.temporal_event_evaluator import DATASET_COLUMNS, FEATURES

EVENT_CLASSES = ("normal", "connection_burst", "periodic_connections", "repeated_failures")
RISK_CLASSES = ("low", "medium", "high")


def load_event_dataset(path: Path):
    raw = path.read_bytes()
    reader = csv.DictReader(raw.decode("utf-8-sig").splitlines())
    if reader.fieldnames is None or len(reader.fieldnames) != len(DATASET_COLUMNS) or set(reader.fieldnames) != set(DATASET_COLUMNS):
        raise ValueError("El CSV de eventos debe conservar exactamente las columnas exportadas.")
    rows, groups, splits, event_labels, risk_labels = [], [], [], [], []
    group_splits = {}
    for number, row in enumerate(reader, 2):
        if None in row or any(v is None for v in row.values()):
            raise ValueError(f"Fila {number}: columnas incompletas.")
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", row["group_id"]):
            raise ValueError(f"Fila {number}: falta un grupo experimental anónimo.")
        if row["evaluation_split"] not in {"training", "test"}:
            raise ValueError(f"Fila {number}: partición no válida.")
        group = row["group_id"]
        split = row["evaluation_split"]
        if group_splits.setdefault(group, split) != split:
            raise ValueError("Un grupo relacionado aparece en entrenamiento y prueba.")
        if row["reference_reviewed"] != "yes":
            raise ValueError("Cada referencia necesita revisión independiente; no uses predicciones como etiquetas.")
        if row["reference_event_type"] not in EVENT_CLASSES or row["reference_risk_level"] not in RISK_CLASSES:
            raise ValueError("Faltan etiquetas de evento y riesgo válidas.")
        feature = [float(row[name]) for name in FEATURES]
        if any(not math.isfinite(v) or v < 0 for v in feature):
            raise ValueError("Las características deben ser finitas y no negativas.")
        if any(feature[FEATURES.index(name)] > 1 for name in ("failure_ratio", "tcp_ratio", "http_ratio")):
            raise ValueError("Las proporciones no pueden exceder uno.")
        n = feature[0]
        if n != int(n) or n > 2048 or feature[1] >= 30_000 or feature[2] >= 30_000:
            raise ValueError("Contadores o tiempos fuera de la ventana de 30 segundos.")
        if feature[5] != int(feature[5]) or feature[5] > n:
            raise ValueError("Los destinos no pueden superar las observaciones.")
        if n == 0 and any(feature):
            raise ValueError("Una ventana vacía requiere características en cero.")
        start, end = float(row["window_start_ms"]), float(row["window_end_ms"])
        if not math.isfinite(start) or not math.isfinite(end) or not 0 <= start < end <= 86_400_000:
            raise ValueError("Intervalo de ventana no válido.")
        if float(row["window_end_ms"]) - float(row["window_start_ms"]) != 30_000:
            raise ValueError("El modelo usa unidades completas de 30 segundos.")
        rows.append(feature); groups.append(group); splits.append(split)
        event_labels.append(row["reference_event_type"]); risk_labels.append(row["reference_risk_level"])
    if not rows:
        raise ValueError("No hay observaciones etiquetadas; no se entrenará un modelo vacío.")
    return rows, groups, splits, event_labels, risk_labels, hashlib.sha256(raw).hexdigest()


def train_event_model(path: Path, output: Path, *, data_kind="pilot", approve_inference=False, trees=100):
    import joblib
    import numpy as np
    import sklearn
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.tree import DecisionTreeClassifier
    from sklearn.dummy import DummyClassifier
    from sklearn.base import clone
    from sklearn.model_selection import StratifiedGroupKFold
    from sklearn.metrics import classification_report, confusion_matrix, f1_score

    if data_kind not in {"pilot", "study"} or (approve_inference and data_kind != "study"):
        raise ValueError("Solo un conjunto study revisado puede aprobarse para inferencia.")
    rows, groups, splits, events, risks, digest = load_event_dataset(path)
    training = [i for i, s in enumerate(splits) if s == "training"]
    heldout = [i for i, s in enumerate(splits) if s == "test"]
    if not training or not heldout:
        raise ValueError("Se necesitan entrenamiento y prueba separados por grupo.")
    models, evaluations = {}, {}
    eligible = True
    for head, labels, classes in (("event", events, EVENT_CLASSES), ("risk", risks, RISK_CLASSES)):
        present = sorted({labels[i] for i in training})
        if head == "event" and ("normal" not in present or len(present) < 2):
            raise ValueError("La detección requiere controles normales y al menos un patrón.")
        if head == "risk" and set(present) != set(classes):
            raise ValueError("El riesgo requiere low, medium y high en entrenamiento.")
        if {labels[i] for i in heldout} != set(present):
            raise ValueError("La prueba debe incluir todas las clases entrenadas.")
        for label in present:
            if len({groups[i] for i in training if labels[i] == label}) < 3:
                raise ValueError("Se necesitan al menos tres grupos de entrenamiento por clase.")
            if len({groups[i] for i in heldout if labels[i] == label}) < 2:
                raise ValueError("Se necesitan al menos dos grupos reservados por clase.")
        x = np.asarray([rows[i] for i in training]); y = np.asarray([labels[i] for i in training])
        g = np.asarray([groups[i] for i in training])
        xt = np.asarray([rows[i] for i in heldout]); yt = np.asarray([labels[i] for i in heldout])
        folds = list(StratifiedGroupKFold(n_splits=3, shuffle=True, random_state=2026).split(x, y, g))
        if any(set(y[a]) != set(present) or set(y[b]) != set(present) for a,b in folds):
            raise ValueError("Los grupos no representan todas las clases en cada pliegue.")
        candidates = {"dummy": DummyClassifier(strategy="prior"),
                      "tree": DecisionTreeClassifier(max_depth=6, min_samples_leaf=2, class_weight="balanced", random_state=2026),
                      "forest": RandomForestClassifier(n_estimators=trees, min_samples_leaf=2, class_weight="balanced", random_state=2026, n_jobs=1)}
        scores = {}
        for name, candidate in candidates.items():
            pred = np.empty(len(y), dtype=object)
            for a,b in folds:
                pred[b] = clone(candidate).fit(x[a], y[a]).predict(x[b])
            scores[name] = float(f1_score(y, pred, average="macro", zero_division=0))
        chosen = max(("tree", "forest"), key=lambda name: scores[name])
        model = clone(candidates[chosen]).fit(x, y)
        pred = model.predict(xt)
        baseline = clone(candidates["dummy"]).fit(x, y).predict(xt)
        test_f1 = float(f1_score(yt, pred, average="macro", zero_division=0))
        baseline_f1 = float(f1_score(yt, baseline, average="macro", zero_division=0))
        eligible &= test_f1 > baseline_f1
        models[head] = model
        evaluations[head] = dict(model=chosen, classes=present, grouped_cv_macro_f1=scores,
            held_out_macro_f1=test_f1, dummy_macro_f1=baseline_f1,
            classification_report=classification_report(yt, pred, labels=present, output_dict=True, zero_division=0),
            confusion_matrix=confusion_matrix(yt, pred, labels=present).tolist())
    output.mkdir(parents=True, exist_ok=True)
    model_path = output / "classifier.joblib"
    with tempfile.NamedTemporaryFile(dir=output, suffix=".joblib.tmp", delete=False) as handle:
        temporary_model = Path(handle.name)
    try:
        joblib.dump(models, temporary_model)
        os.replace(temporary_model, model_path)
    finally:
        temporary_model.unlink(missing_ok=True)
    manifest = dict(schema_version=1, feature_names=list(FEATURES), window_ms=30_000,
        dataset_sha256=digest, scikit_learn_version=sklearn.__version__, data_kind=data_kind,
        training_rows=len(training), test_rows=len(heldout), evaluations=evaluations,
        artifact_sha256=hashlib.sha256(model_path.read_bytes()).hexdigest(),
        enabled_for_inference=bool(approve_inference and eligible),
        interpretation="Clasificación experimental de patrones y riesgo; no confirma intención maliciosa ni mejora frente a Suricata.")
    manifest_temp = output / "manifest.json.tmp"
    manifest_temp.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(manifest_temp, output / "manifest.json")
    return manifest
