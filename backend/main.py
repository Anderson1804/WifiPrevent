from datetime import datetime, timezone
from hashlib import sha256
from typing import Annotated, Literal
from uuid import UUID, uuid4

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import select, text
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from database import get_session
from models import ConnectionCheck

app = FastAPI(title="WiFiPrevent local", version="0.2.0",
    description="Metadatos e historial por instalación. Sin análisis de amenazas. Solo desarrollo local.")

class ConnectionReading(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    ssid: str | None = Field(default=None, max_length=128)
    rssi_dbm: int | None = Field(default=None, ge=-126, le=-1)
    frequency_mhz: int | None = Field(default=None, ge=1, le=100000)
    link_speed_mbps: int | None = Field(default=None, ge=1, le=100000)
    internet_validated: bool
    captive_portal: bool

class Receipt(BaseModel):
    receipt_id: UUID
    received_at: datetime
    status: Literal["received"] = "received"
    risk_level: None = None
    analysis_performed: bool = False
    message: str = "Los datos se guardaron en el historial. Riesgo no evaluado."

class HistoryItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    receipt_id: UUID
    received_at: datetime
    ssid: str | None
    rssi_dbm: int | None
    frequency_mhz: int | None
    link_speed_mbps: int | None
    internet_validated: bool
    captive_portal: bool
    risk_level: None = None
    analysis_performed: bool = False

class HistoryPage(BaseModel):
    items: list[HistoryItem]
    next_before: UUID | None = None

# A random installation secret scopes local history. This is not a user login.
def owner_hash(authorization: Annotated[str | None, Header()] = None):
    import re
    if not authorization or not re.fullmatch(r"Bearer [0-9a-f]{64}", authorization):
        raise HTTPException(401, "Identificación de instalación requerida.")
    return sha256(authorization[7:].encode()).hexdigest()

DB = Annotated[Session, Depends(get_session)]
Owner = Annotated[str, Depends(owner_hash)]

@app.exception_handler(SQLAlchemyError)
async def database_error(request: Request, exc: SQLAlchemyError):
    return JSONResponse(status_code=503, content={"detail":
        "Base de datos no disponible. No se confirmó el guardado; puedes reintentar."})

@app.get("/health")
def health(session: DB):
    session.execute(text("SELECT 1"))
    return {"status": "ok", "service": "wifiprevent", "version": "0.2.0", "database": "postgresql"}

@app.post("/api/v1/connection-checks", response_model=Receipt)
def receive_connection(reading: ConnectionReading, session: DB, owner: Owner,
        x_request_id: Annotated[UUID, Header()]):
    values = reading.model_dump()
    # A repeated request after a lost HTTP response must not create another row.
    statement = insert(ConnectionCheck).values(receipt_id=uuid4(), owner_hash=owner,
        request_id=x_request_id, received_at=datetime.now(timezone.utc), **values,
        risk_level=None, analysis_performed=False).on_conflict_do_nothing(
            constraint="uq_checks_owner_request")
    session.execute(statement)
    row = session.scalar(select(ConnectionCheck).where(
        ConnectionCheck.owner_hash == owner, ConnectionCheck.request_id == x_request_id))
    if any(getattr(row, key) != value for key, value in values.items()):
        session.rollback()
        raise HTTPException(409, "El identificador de envío ya corresponde a otros datos.")
    session.commit()
    return Receipt(receipt_id=row.receipt_id, received_at=row.received_at)

@app.get("/api/v1/connection-checks", response_model=HistoryPage)
def history(session: DB, owner: Owner, limit: Annotated[int, Query(ge=1, le=100)] = 20,
            before: UUID | None = None):
    statement = select(ConnectionCheck).where(ConnectionCheck.owner_hash == owner)
    if before is not None:
        cursor = session.scalar(select(ConnectionCheck).where(
            ConnectionCheck.owner_hash == owner, ConnectionCheck.receipt_id == before))
        if cursor is None:
            raise HTTPException(404, "Registro no encontrado.")
        from sqlalchemy import tuple_
        statement = statement.where(tuple_(ConnectionCheck.received_at, ConnectionCheck.receipt_id) <
                                    tuple_(cursor.received_at, cursor.receipt_id))
    rows = session.scalars(statement.order_by(ConnectionCheck.received_at.desc(),
                                             ConnectionCheck.receipt_id.desc()).limit(limit + 1)).all()
    return HistoryPage(items=[HistoryItem.model_validate(row) for row in rows[:limit]],
                       next_before=rows[limit - 1].receipt_id if len(rows) > limit else None)
