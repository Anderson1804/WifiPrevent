import json
import os
from pathlib import Path

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

BACKEND_ROOT = Path(__file__).resolve().parents[2]

def database_url():
    value = os.environ.get("DATABASE_URL")
    if value:
        return value
    config = (
            BACKEND_ROOT
            / ".local"
            / "app-config.json"
    )
    if not config.exists():
        raise RuntimeError("Configura DATABASE_URL o ejecuta la preparación de PostgreSQL local.")
    return json.loads(config.read_text(encoding="utf-8"))["database_url"]

class Base(DeclarativeBase):
    pass

engine = create_engine(database_url(), pool_pre_ping=True, hide_parameters=True,
                       connect_args={"connect_timeout": 5, "options": "-c timezone=UTC"})
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)

def get_session():
    with SessionLocal() as session:
        yield session
