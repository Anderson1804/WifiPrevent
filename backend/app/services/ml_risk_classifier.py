"""Optional supervised risk-level inference for complete, adequate captures.

The default application remains operational without a trained artifact. Model
files are created only by the repository's training command from a validated,
locally supplied dataset; never load artifacts from an untrusted source.
"""

from __future__ import annotations

import hashlib
import json
import logging
from functools import lru_cache
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping


logger = logging.getLogger(__name__)

FEATURE_SCHEMA_VERSION = "1"
MODEL_CLASSES = ("low", "medium", "high")
NUMERIC_FEATURES = (
    "duration_seconds",
    "received_bytes",
    "transmitted_bytes",
    "received_packets",
    "transmitted_packets",
    "parsed_packets",
    "unparsed_packets",
    "ipv4_packets",
    "ipv6_packets",
    "tcp_packets",
    "udp_packets",
    "icmp_packets",
    "other_transport_packets",
    "dns_packets",
    "http_packets",
    "tls_or_quic_packets",
    "unique_destinations",
    "relay_tcp_connections",
    "relay_udp_datagrams",
    "relay_dns_observations",
    "relay_http_observations",
    "relay_tls_or_quic_observations",
    "relay_other_observations",
    "relay_unique_destinations",
)
CATEGORICAL_FEATURES = (
    "security_type",
    "captive_portal",
    "capture_mode",
    "relay_metrics_collected",
)
FEATURE_NAMES = NUMERIC_FEATURES + CATEGORICAL_FEATURES
DATA_DIRECTORY = Path(__file__).resolve().parents[2] / "data"
MODEL_DIRECTORY = DATA_DIRECTORY / "models"
MODEL_PATH = MODEL_DIRECTORY / "risk_level_classifier.joblib"
MANIFEST_PATH = MODEL_DIRECTORY / "risk_level_classifier.json"


@dataclass(frozen=True)
class ModelPrediction:
    level: str
    assessment_version: str


def normalize_features(reading: Mapping[str, Any]) -> list[int | str]:
    """Return model inputs in the versioned feature order, excluding identifiers."""
    values: list[int | str] = []
    for name in NUMERIC_FEATURES:
        value = reading.get(name)
        if type(value) is not int or value < 0:
            raise ValueError(f"La característica {name} debe ser un entero no negativo.")
        values.append(value)

    security_type = reading.get("security_type")
    values.append(str(security_type).strip().upper() if security_type else "UNKNOWN")
    values.append(_normalize_optional_boolean(reading.get("captive_portal")))
    values.append(str(reading.get("capture_mode", "")).strip().lower())
    values.append(_normalize_boolean(reading.get("relay_metrics_collected")))
    return values


def _normalize_boolean(value: Any) -> str:
    if value is True or value == "true" or value == "yes":
        return "yes"
    if value is False or value == "false" or value == "no":
        return "no"
    raise ValueError("La característica booleana debe ser verdadera o falsa.")


def _normalize_optional_boolean(value: Any) -> str:
    if value is None or value == "" or value == "unknown":
        return "unknown"
    return _normalize_boolean(value)


def predict_risk_level(reading: Mapping[str, Any]) -> ModelPrediction | None:
    """Predict only when an intact, compatible local artifact is available."""
    if (
        reading.get("capture_mode") != "full"
        or reading.get("relay_metrics_collected") is not True
        or reading.get("duration_seconds", 0) < 30
        or reading.get("received_packets", 0) + reading.get("transmitted_packets", 0) < 100
    ):
        return None

    loaded = _load_default_model()
    if loaded is None:
        return None
    model, manifest = loaded

    try:
        prediction = str(model.predict([normalize_features(reading)])[0])
    except Exception:
        logger.warning("No se pudo aplicar el modelo local; se conservará la evaluación por reglas.")
        return None
    if prediction not in MODEL_CLASSES:
        logger.warning("El modelo local devolvió una clase desconocida; se conservarán las reglas.")
        return None
    return ModelPrediction(
        level=prediction,
        assessment_version=f"ml-{manifest['model_version']}",
    )


def _load_default_model() -> tuple[Any, dict[str, Any]] | None:
    if not MODEL_PATH.is_file() or not MANIFEST_PATH.is_file():
        return None
    try:
        return _load_model_cached(
            str(MODEL_PATH), str(MANIFEST_PATH),
            MODEL_PATH.stat().st_mtime_ns, MANIFEST_PATH.stat().st_mtime_ns,
        )
    except OSError:
        return None


@lru_cache(maxsize=2)
def _load_model_cached(
        model_path_text: str,
        manifest_path_text: str,
        model_modified_ns: int,
        manifest_modified_ns: int,
) -> tuple[Any, dict[str, Any]] | None:
    del model_modified_ns, manifest_modified_ns
    model_path = Path(model_path_text)
    manifest_path = Path(manifest_path_text)
    try:
        manifest_bytes = manifest_path.read_bytes()
        artifact_bytes = model_path.read_bytes()
        manifest = json.loads(manifest_bytes.decode("utf-8"))
        if manifest.get("feature_schema_version") != FEATURE_SCHEMA_VERSION:
            raise ValueError("versión de características no compatible")
        if manifest.get("feature_names") != list(FEATURE_NAMES):
            raise ValueError("características del modelo no compatibles")
        if manifest.get("enabled_for_inference") is not True:
            raise ValueError("el resultado no superó el criterio de activación")
        if manifest.get("artifact_sha256") != hashlib.sha256(artifact_bytes).hexdigest():
            raise ValueError("la huella del modelo no coincide")

        # joblib uses Python pickle internally. Only load the fixed local artifact
        # written by train_risk_model.py, never a path supplied by an API caller.
        import joblib
        import sklearn

        if manifest.get("scikit_learn_version") != sklearn.__version__:
            raise ValueError("la versión de scikit-learn no coincide con el artefacto")
        model = joblib.load(model_path)
        if tuple(manifest.get("classes", ())) != MODEL_CLASSES:
            raise ValueError("clases del modelo no compatibles")
        return model, manifest
    except Exception:
        logger.warning("El artefacto ML local no está disponible o no es compatible; se usarán reglas.")
        return None
