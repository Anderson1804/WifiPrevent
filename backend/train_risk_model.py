"""Train and evaluate the optional supervised risk model from an approved CSV."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from app.services.ml_training import train_and_evaluate
from app.services.ml_risk_classifier import MODEL_DIRECTORY


DEFAULT_DATASET = (
    Path(__file__).resolve().parent / "data" / "observations" / "risk_observations.csv"
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "dataset",
        nargs="?",
        type=Path,
        default=DEFAULT_DATASET,
        help="CSV validado y etiquetado de observaciones autorizadas.",
    )
    parser.add_argument(
        "--model-directory",
        type=Path,
        default=MODEL_DIRECTORY,
        help="Carpeta local donde guardar el modelo y sus métricas.",
    )
    args = parser.parse_args()
    manifest = train_and_evaluate(args.dataset, args.model_directory)
    print(json.dumps({
        "model_name": manifest["model_name"],
        "model_version": manifest["model_version"],
        "cross_validation": manifest["cross_validation"][manifest["model_name"]],
        "held_out_test": manifest["held_out_test"],
        "model_directory": str(args.model_directory.resolve()),
        "message": "Reinicia el backend para cargar el artefacto ML local.",
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
