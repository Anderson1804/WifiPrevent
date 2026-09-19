from fastapi import APIRouter
from sqlalchemy import text

from app.api.dependencies import DatabaseSession


router = APIRouter(tags=["system"])


@router.get("/health")
def health(session: DatabaseSession) -> dict[str, str]:
    session.execute(text("SELECT 1"))

    return {
        "status": "ok",
        "service": "wifiprevent",
        "version": "0.2.0",
        "database": "postgresql",
    }