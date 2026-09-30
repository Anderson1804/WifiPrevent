"""Export an unlabeled observation using only the training feature allowlist."""

import csv
from collections.abc import Mapping
from io import StringIO
from typing import Any

from app.services.ml_risk_classifier import FEATURE_NAMES, normalize_features
from app.services.ml_training import CSV_COLUMNS


_SECURITY_TYPES = {
    "OPEN", "WEP", "WPA_WPA2_PSK", "WPA_WPA2_ENTERPRISE", "WPA3_SAE",
    "OWE", "OWE_TRANSITION", "OTHER_OR_UNKNOWN", "UNKNOWN",
}


def export_training_observation(reading: Mapping[str, Any]) -> str:
    if (
        reading.get("capture_mode") != "full"
        or reading.get("relay_metrics_collected") is not True
        or reading.get("duration_seconds", 0) < 30
        or reading.get("received_packets", 0) + reading.get("transmitted_packets", 0) < 100
    ):
        raise ValueError(
            "El CSV requiere una captura completa de al menos 30 segundos, "
            "100 paquetes y observaciones del relé disponibles."
        )

    # A legacy or client-provided free-form value must not leak into a spreadsheet.
    security_type = str(reading.get("security_type") or "UNKNOWN").strip().upper()
    features = dict(reading)
    features["security_type"] = security_type if security_type in _SECURITY_TYPES else "UNKNOWN"
    values = normalize_features(features)
    row = dict(zip(FEATURE_NAMES, values, strict=True))
    relay_total = row["relay_tcp_connections"] + row["relay_udp_datagrams"]
    relay_categories = sum(row[name] for name in (
        "relay_dns_observations", "relay_http_observations",
        "relay_tls_or_quic_observations", "relay_other_observations",
    ))
    if relay_total != relay_categories or row["relay_unique_destinations"] > relay_total:
        raise ValueError("Los contadores históricos del relé no son compatibles con la plantilla.")

    output = StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=CSV_COLUMNS, lineterminator="\n")
    writer.writeheader()
    # Deliberately omit scenario, partition, and reference label. In particular,
    # never copy the application's risk prediction into the reference label.
    writer.writerow(row)
    return output.getvalue()
