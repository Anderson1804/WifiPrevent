import json
import os
from pathlib import Path

from sqlalchemy.engine import make_url

# Never run persistence tests against the application's real database.
BACKEND_ROOT = Path(__file__).resolve().parents[1]

config = json.loads(
    (BACKEND_ROOT / ".local" / "app-config.json")
    .read_text(encoding="utf-8")
)
url = make_url(config["database_url"]).set(database="wifiprevent_test")
os.environ["DATABASE_URL"] = url.render_as_string(hide_password=False)

from uuid import uuid4
import secrets

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.db.session import engine
from main import app


@pytest.fixture(scope="session", autouse=True)
def migrate():
    assert engine.url.database == "wifiprevent_test"
    command.upgrade(Config(str(BACKEND_ROOT / "alembic.ini")), "head")


@pytest.fixture()
def client():
    assert engine.url.database == "wifiprevent_test"
    with engine.begin() as connection:
        connection.execute(text("TRUNCATE connection_checks, analysis_sessions"))
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture()
def headers():
    return {
        "Authorization": "Bearer " + secrets.token_hex(32),
        "X-Request-ID": str(uuid4()),
    }
