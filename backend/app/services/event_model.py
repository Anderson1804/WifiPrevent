"""Load only locally trained, explicitly approved event artifacts."""
import hashlib
import json
import pickle
import logging
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "data" / "event_models"
LOGGER = logging.getLogger(__name__)
_cached = None
_signature = None


def predict_event(features):
    global _cached, _signature
    from app.services.temporal_event_evaluator import FEATURES
    model_path, manifest_path = ROOT / "classifier.joblib", ROOT / "manifest.json"
    if not model_path.is_file() or not manifest_path.is_file():
        return None
    try:
        signature = (str(model_path.resolve()), model_path.stat().st_mtime_ns, manifest_path.stat().st_mtime_ns)
        if signature != _signature:
            _cached = None
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            if (manifest.get("enabled_for_inference") is not True
                    or manifest.get("data_kind") != "study"
                    or manifest.get("window_ms") != 30_000
                    or manifest.get("feature_names") != list(FEATURES)
                    or manifest.get("artifact_sha256") != hashlib.sha256(model_path.read_bytes()).hexdigest()):
                return None
            import joblib
            import sklearn
            if manifest.get("scikit_learn_version") != sklearn.__version__:
                return None
            _cached = (joblib.load(model_path), manifest)
            _signature = signature
        models, manifest = _cached
        reading = [[features[name] for name in FEATURES]]
        event = str(models["event"].predict(reading)[0])
        risk = str(models["risk"].predict(reading)[0])
        if event not in {"normal", "connection_burst", "periodic_connections", "repeated_failures"} or risk not in {"low", "medium", "high"}:
            return None
        return {"event_type": event, "risk_level": risk, "method": f"ml-event-{manifest['dataset_sha256'][:12]}"}
    except (OSError, ValueError, KeyError, ImportError, TypeError, EOFError, AttributeError, pickle.UnpicklingError):
        LOGGER.warning("Modelo de eventos no disponible; se conservan los patrones observados.")
        return None
