from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

from app.api.routes import (
    analysis_sessions_router,
    connection_checks_router,
    health_router,
)


app = FastAPI(
    title="WiFiPrevent local",
    version="0.2.0",
    description=(
        "Metadatos e historial por instalación. "
        "Sin análisis de amenazas. Solo desarrollo local."
    ),
)

app.include_router(health_router)
app.include_router(connection_checks_router)
app.include_router(analysis_sessions_router)


@app.exception_handler(SQLAlchemyError)
async def database_error(
        request: Request,
        exc: SQLAlchemyError,
) -> JSONResponse:
    return JSONResponse(
        status_code=503,
        content={
            "detail": (
                "Base de datos no disponible. "
                "No se confirmó el guardado; puedes reintentar."
            )
        },
    )
