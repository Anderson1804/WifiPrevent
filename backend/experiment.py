"""Local laboratory tools. Never invent reference labels or research results."""
import argparse
import json
from pathlib import Path

from app.services.experiment_evaluation import write_report
from app.services.event_training import train_event_model
from app.services.lab_receiver import LabServer
from app.services.suricata_importer import import_suricata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    receiver = commands.add_parser("receiver", help="Receptor de tráfico sintético para el celular.")
    receiver.add_argument("--bind", default="127.0.0.1")
    receiver.add_argument("--port", type=int, default=8765)
    receiver.add_argument("--allowed-client", help="IP del relé que conecta al receptor.")
    receiver.add_argument("--log", type=Path, required=True)
    report = commands.add_parser("report", help="Calcular indicadores y coincidencias del anexo 2.")
    report.add_argument("book", type=Path)
    report.add_argument("--output", type=Path, required=True)
    eve = commands.add_parser("import-suricata", help="Convertir alertas EVE usando una equivalencia explícita.")
    eve.add_argument("eve", type=Path)
    eve.add_argument("--mapping", type=Path, required=True)
    eve.add_argument("--started-at-utc", required=True)
    eve.add_argument("--output", type=Path, required=True)
    train = commands.add_parser("train-events", help="Entrenar clasificación de patrones y riesgo por ventana.")
    train.add_argument("dataset", type=Path)
    train.add_argument("--output", type=Path, required=True)
    train.add_argument("--data-kind", choices=["pilot", "study"], default="pilot")
    train.add_argument("--approve-inference", action="store_true")
    inspect = commands.add_parser("inspect-capture", help="Evaluar un registro temporal JSON desidentificado.")
    inspect.add_argument("capture", type=Path)
    inspect.add_argument("--output", type=Path, required=True)
    prepare = commands.add_parser("prepare-run", help="Preparar un borrador no revisado con evidencia del receptor.")
    prepare.add_argument("capture", type=Path)
    prepare.add_argument("receiver_log", type=Path)
    prepare.add_argument("--execution-id", required=True)
    prepare.add_argument("--scenario", required=True)
    prepare.add_argument("--pair", required=True)
    prepare.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "receiver":
            with LabServer((args.bind, args.port), args.log, args.allowed_client) as server:
                print("Receptor del laboratorio activo. Ctrl+C para detenerlo.")
                server.serve_forever()
        elif args.command == "report":
            result = write_report(args.book, args.output)
            print(f"Evaluadas {len(result['results'])} ejecuciones. Tipo: {result['data_kind']}.")
        elif args.command == "import-suricata":
            result = import_suricata(args.eve, args.mapping, args.started_at_utc)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            print(f"Importadas {len(result)} alertas mapeadas; no son referencias automáticas.")
        elif args.command == "train-events":
            result = train_event_model(args.dataset, args.output, data_kind=args.data_kind,
                                       approve_inference=args.approve_inference)
            print(json.dumps({"data_kind": result["data_kind"], "enabled_for_inference": result["enabled_for_inference"],
                              "evaluations": result["evaluations"]}, ensure_ascii=False, indent=2))
        elif args.command == "prepare-run":
            from app.services.experiment_preparation import prepare_run
            result = prepare_run(args.capture, args.receiver_log, args.execution_id, args.scenario, args.pair)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
            print("Borrador creado con referencias pendientes; no es una ejecución aprobada del estudio.")
        elif args.command == "inspect-capture":
            from app.schemas.temporal_capture import TemporalCapture
            from app.services.temporal_event_evaluator import evaluate_temporal_events
            document = json.loads(args.capture.read_text(encoding="utf-8-sig"))
            capture = TemporalCapture.model_validate(document.get("temporal_capture", document))
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps([e.model_dump() for e in evaluate_temporal_events(capture)],
                ensure_ascii=False, indent=2), encoding="utf-8")
            print("Patrones evaluados. No se asignaron referencias ni amenazas confirmadas.")
    except KeyboardInterrupt:
        return 0
    except (ValueError, OSError) as exc:
        parser.error(str(exc))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
