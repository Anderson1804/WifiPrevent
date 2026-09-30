"""Dataset validation and grouped evaluation for the supervised risk classifier."""

from __future__ import annotations

import csv
import hashlib
import json
import os
import re
import tempfile
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.services.ml_risk_classifier import (
    FEATURE_NAMES,
    FEATURE_SCHEMA_VERSION,
    MODEL_CLASSES,
    NUMERIC_FEATURES,
)


CSV_COLUMNS = (
    "scenario_id",
    "evaluation_split",
    "reference_risk_level",
    *FEATURE_NAMES,
)
_SCENARIO_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
_TRUE_VALUES = {"true", "yes", "1"}
_FALSE_VALUES = {"false", "no", "0"}


@dataclass(frozen=True)
class TrainingDataset:
    features: list[list[int | str]]
    labels: list[str]
    groups: list[str]
    splits: list[str]
    sha256: str


def load_training_dataset(path: Path) -> TrainingDataset:
    """Read one labeled row per capture; reject PII and train/test group leakage."""
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise ValueError("El CSV debe estar codificado en UTF-8.") from exc

    reader = csv.DictReader(text.splitlines())
    if (
        reader.fieldnames is None
        or len(reader.fieldnames) != len(CSV_COLUMNS)
        or set(reader.fieldnames) != set(CSV_COLUMNS)
    ):
        raise ValueError(
            "El CSV debe contener exactamente las columnas de la plantilla; "
            "no agregues SSID, direcciones, dominios, usuario ni contenido."
        )

    rows: list[list[int | str]] = []
    labels: list[str] = []
    groups: list[str] = []
    splits: list[str] = []
    group_split: dict[str, str] = {}
    group_label: dict[str, str] = {}
    for line_number, raw_row in enumerate(reader, start=2):
        if None in raw_row or any(value is None for value in raw_row.values()):
            raise ValueError(f"La fila {line_number} tiene celdas faltantes o columnas adicionales.")
        row = {key: (value or "").strip() for key, value in raw_row.items()}
        scenario_id = row["scenario_id"]
        if not _SCENARIO_PATTERN.fullmatch(scenario_id):
            raise ValueError(f"La fila {line_number} necesita un código de escenario no identificable.")
        split = row["evaluation_split"].lower()
        if split not in {"training", "test"}:
            raise ValueError(f"La fila {line_number}: evaluation_split debe ser training o test.")
        label = row["reference_risk_level"].lower()
        if label not in MODEL_CLASSES:
            raise ValueError(f"La fila {line_number}: reference_risk_level debe ser low, medium o high.")
        if row["capture_mode"].lower() != "full":
            raise ValueError(f"La fila {line_number}: ML requiere una captura completa.")
        if _parse_boolean(row["relay_metrics_collected"], line_number) is not True:
            raise ValueError(f"La fila {line_number}: faltan las métricas del relé para entrenar.")

        numeric_values: list[int] = []
        for name in NUMERIC_FEATURES:
            value = row[name]
            if not value.isdecimal():
                raise ValueError(f"La fila {line_number}: {name} debe ser un entero no negativo.")
            numeric_values.append(int(value))
        if numeric_values[0] < 30 or numeric_values[3] + numeric_values[4] < 100:
            raise ValueError(f"La fila {line_number}: la muestra no alcanza calidad adecuada.")

        numeric = dict(zip(NUMERIC_FEATURES, numeric_values, strict=True))
        relay_total = numeric["relay_tcp_connections"] + numeric["relay_udp_datagrams"]
        relay_categories = sum(numeric[name] for name in (
            "relay_dns_observations",
            "relay_http_observations",
            "relay_tls_or_quic_observations",
            "relay_other_observations",
        ))
        if relay_total != relay_categories:
            raise ValueError(f"La fila {line_number}: los contadores del relé no coinciden.")
        if numeric["relay_unique_destinations"] > relay_total:
            raise ValueError(f"La fila {line_number}: destinos únicos supera observaciones del relé.")

        security_type = row["security_type"].upper() or "UNKNOWN"
        captive_portal = _parse_optional_boolean(row["captive_portal"], line_number)
        categories = [security_type, captive_portal, "full", "yes"]
        rows.append([*numeric_values, *categories])
        labels.append(label)
        groups.append(scenario_id)
        splits.append(split)

        prior_split = group_split.setdefault(scenario_id, split)
        if prior_split != split:
            raise ValueError(
                f"El escenario {scenario_id} aparece tanto en training como en test; "
                "separar por escenario evita fuga de información."
            )
        prior_label = group_label.setdefault(scenario_id, label)
        if prior_label != label:
            raise ValueError(
                f"El escenario {scenario_id} tiene más de un nivel de referencia; "
                "divide la captura en unidades de análisis coherentes."
            )

    if not rows:
        raise ValueError("El CSV no contiene observaciones.")
    return TrainingDataset(
        features=rows,
        labels=labels,
        groups=groups,
        splits=splits,
        sha256=hashlib.sha256(raw).hexdigest(),
    )


def _parse_boolean(value: str, line_number: int) -> bool:
    normalized = value.strip().lower()
    if normalized in _TRUE_VALUES:
        return True
    if normalized in _FALSE_VALUES:
        return False
    raise ValueError(f"La fila {line_number}: el valor debe ser verdadero o falso.")


def _parse_optional_boolean(value: str, line_number: int) -> str:
    normalized = value.strip().lower()
    if not normalized or normalized == "unknown":
        return "unknown"
    return "yes" if _parse_boolean(normalized, line_number) else "no"


def train_and_evaluate(
        path: Path,
        model_directory: Path,
        *,
        random_forest_trees: int = 100,
) -> dict[str, Any]:
    """Compare candidate classifiers with grouped CV; publish only the best pipeline."""
    dataset = load_training_dataset(path)
    train_indices = [i for i, split in enumerate(dataset.splits) if split == "training"]
    test_indices = [i for i, split in enumerate(dataset.splits) if split == "test"]
    if not train_indices or not test_indices:
        raise ValueError("El conjunto necesita observaciones de training y de test.")

    train_labels = {dataset.labels[i] for i in train_indices}
    test_labels = {dataset.labels[i] for i in test_indices}
    if train_labels != set(MODEL_CLASSES) or test_labels != set(MODEL_CLASSES):
        raise ValueError("Training y test deben incluir las clases low, medium y high.")

    train_groups_by_label: dict[str, set[str]] = defaultdict(set)
    test_groups_by_label: dict[str, set[str]] = defaultdict(set)
    for i in train_indices:
        train_groups_by_label[dataset.labels[i]].add(dataset.groups[i])
    for i in test_indices:
        test_groups_by_label[dataset.labels[i]].add(dataset.groups[i])
    if any(len(train_groups_by_label[label]) < 5 for label in MODEL_CLASSES):
        raise ValueError(
            "Se necesitan al menos cinco escenarios distintos por nivel de riesgo "
            "en training para la validación cruzada de cinco grupos."
        )
    if any(len(test_groups_by_label[label]) < 2 for label in MODEL_CLASSES):
        raise ValueError(
            "Se necesitan al menos dos escenarios distintos por nivel en test "
            "para estimar métricas por clase."
        )

    try:
        import numpy as np
        import sklearn
        from sklearn.base import clone
        from sklearn.compose import ColumnTransformer
        from sklearn.dummy import DummyClassifier
        from sklearn.ensemble import RandomForestClassifier
        from sklearn.impute import SimpleImputer
        from sklearn.linear_model import LogisticRegression
        from sklearn.metrics import (
            accuracy_score,
            balanced_accuracy_score,
            confusion_matrix,
            f1_score,
            precision_recall_fscore_support,
        )
        from sklearn.model_selection import StratifiedGroupKFold
        from sklearn.pipeline import Pipeline
        from sklearn.preprocessing import OneHotEncoder, StandardScaler
        from sklearn.tree import DecisionTreeClassifier
        import joblib
    except ImportError as exc:
        raise RuntimeError(
            "Falta scikit-learn. Instálalo con backend\\.venv\\Scripts\\python.exe "
            "-m pip install -r backend\\requirements.txt."
        ) from exc

    x_train = np.asarray([dataset.features[i] for i in train_indices], dtype=object)
    y_train = np.asarray([dataset.labels[i] for i in train_indices], dtype=str)
    groups_train = np.asarray([dataset.groups[i] for i in train_indices], dtype=str)
    x_test = np.asarray([dataset.features[i] for i in test_indices], dtype=object)
    y_test = np.asarray([dataset.labels[i] for i in test_indices], dtype=str)

    cv = StratifiedGroupKFold(n_splits=5, shuffle=True, random_state=2026)
    folds = list(cv.split(x_train, y_train, groups_train))
    for train_fold, validation_fold in folds:
        if set(y_train[train_fold]) != set(MODEL_CLASSES) or set(y_train[validation_fold]) != set(MODEL_CLASSES):
            raise ValueError(
                "La distribución por escenario no permite representar las tres clases "
                "en cada pliegue; agrega escenarios y vuelve a separar los datos."
            )

    numeric_indices = list(range(len(NUMERIC_FEATURES)))
    categorical_indices = list(range(len(NUMERIC_FEATURES), len(FEATURE_NAMES)))

    def make_pipeline(classifier):
        preprocess = ColumnTransformer(
            transformers=[
                (
                    "numeric",
                    Pipeline([
                        ("imputer", SimpleImputer(strategy="constant", fill_value=0)),
                        ("scale", StandardScaler()),
                    ]),
                    numeric_indices,
                ),
                ("categorical", OneHotEncoder(handle_unknown="ignore"), categorical_indices),
            ],
            remainder="drop",
        )
        return Pipeline([("features", preprocess), ("classifier", classifier)])

    candidates = {
        "dummy_prior": DummyClassifier(strategy="prior"),
        "logistic_regression": LogisticRegression(
            max_iter=2_000, class_weight="balanced", random_state=2026,
        ),
        "decision_tree": DecisionTreeClassifier(
            class_weight="balanced", max_depth=6, min_samples_leaf=2, random_state=2026,
        ),
        "random_forest": RandomForestClassifier(
            n_estimators=random_forest_trees, class_weight="balanced", min_samples_leaf=2,
            max_features="sqrt", random_state=2026, n_jobs=1,
        ),
    }
    cv_results: dict[str, dict[str, float]] = {}
    for name, classifier in candidates.items():
        predictions = np.empty(len(y_train), dtype=object)
        for train_fold, validation_fold in folds:
            pipeline = make_pipeline(clone(classifier))
            pipeline.fit(x_train[train_fold], y_train[train_fold])
            predictions[validation_fold] = pipeline.predict(x_train[validation_fold])
        cv_results[name] = _metrics(y_train.tolist(), predictions.tolist(), MODEL_CLASSES, {
            "accuracy_score": accuracy_score,
            "balanced_accuracy_score": balanced_accuracy_score,
            "confusion_matrix": confusion_matrix,
            "f1_score": f1_score,
            "precision_recall_fscore_support": precision_recall_fscore_support,
        })

    selected_name = max(
        (name for name in candidates if name != "dummy_prior"),
        key=lambda name: (cv_results[name]["macro_f1"], cv_results[name]["balanced_accuracy"]),
    )
    selected = make_pipeline(clone(candidates[selected_name]))
    selected.fit(x_train, y_train)
    baseline = make_pipeline(clone(candidates["dummy_prior"]))
    baseline.fit(x_train, y_train)
    selected_test = _metrics(y_test.tolist(), selected.predict(x_test).tolist(), MODEL_CLASSES, {
        "accuracy_score": accuracy_score,
        "balanced_accuracy_score": balanced_accuracy_score,
        "confusion_matrix": confusion_matrix,
        "f1_score": f1_score,
        "precision_recall_fscore_support": precision_recall_fscore_support,
    })
    baseline_test = _metrics(y_test.tolist(), baseline.predict(x_test).tolist(), MODEL_CLASSES, {
        "accuracy_score": accuracy_score,
        "balanced_accuracy_score": balanced_accuracy_score,
        "confusion_matrix": confusion_matrix,
        "f1_score": f1_score,
        "precision_recall_fscore_support": precision_recall_fscore_support,
    })

    model_directory.mkdir(parents=True, exist_ok=True)
    model_path = model_directory / "risk_level_classifier.joblib"
    manifest_path = model_directory / "risk_level_classifier.json"
    with tempfile.NamedTemporaryFile(dir=model_directory, suffix=".joblib.tmp", delete=False) as handle:
        temporary_model = Path(handle.name)
    try:
        joblib.dump(selected, temporary_model)
        artifact_hash = hashlib.sha256(temporary_model.read_bytes()).hexdigest()
        manifest: dict[str, Any] = {
            "feature_schema_version": FEATURE_SCHEMA_VERSION,
            "feature_names": list(FEATURE_NAMES),
            "classes": list(MODEL_CLASSES),
            "model_name": selected_name,
            "model_version": f"risk-{dataset.sha256[:12]}",
            "enabled_for_inference": selected_test["macro_f1"] > baseline_test["macro_f1"],
            "trained_at_utc": datetime.now(timezone.utc).isoformat(),
            "dataset_sha256": dataset.sha256,
            "scikit_learn_version": sklearn.__version__,
            "artifact_sha256": artifact_hash,
            "training_rows": len(train_indices),
            "test_rows": len(test_indices),
            "training_scenarios_by_class": {
                label: len(train_groups_by_label[label]) for label in MODEL_CLASSES
            },
            "test_scenarios_by_class": {
                label: len(test_groups_by_label[label]) for label in MODEL_CLASSES
            },
            "cross_validation": cv_results,
            "held_out_test": {
                "selected_model": selected_test,
                "dummy_baseline": baseline_test,
            },
            "interpretation": (
                "Métricas experimentales del conjunto autorizado indicado por su huella; "
                "no son resultados de campo ni evidencia de una amenaza real. "
                "El modelo se habilita solo si F1 macro en test supera la línea base."
            ),
        }
        temporary_manifest = manifest_path.with_suffix(".json.tmp")
        temporary_manifest.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary_model, model_path)
        os.replace(temporary_manifest, manifest_path)
    finally:
        if temporary_model.exists():
            temporary_model.unlink()

    return manifest


def _metrics(y_true, y_pred, classes, metric_functions) -> dict[str, Any]:
    precision, recall, f1, support = metric_functions["precision_recall_fscore_support"](
        y_true, y_pred, labels=list(classes), average=None, zero_division=0,
    )
    per_class = {
        label: {
            "precision": float(precision[i]),
            "recall": float(recall[i]),
            "f1": float(f1[i]),
            "support": int(support[i]),
        }
        for i, label in enumerate(classes)
    }
    return {
        "accuracy": float(metric_functions["accuracy_score"](y_true, y_pred)),
        "balanced_accuracy": float(metric_functions["balanced_accuracy_score"](y_true, y_pred)),
        "macro_f1": float(metric_functions["f1_score"](
            y_true, y_pred, labels=list(classes), average="macro", zero_division=0,
        )),
        "per_class": per_class,
        "confusion_matrix": metric_functions["confusion_matrix"](
            y_true, y_pred, labels=list(classes),
        ).astype(int).tolist(),
    }
