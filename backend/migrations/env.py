from alembic import context

from app.db.session import Base, engine
from app.db.models.connection_check import ConnectionCheck

def run_migrations() -> None:
    with engine.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=Base.metadata
        )

        with context.begin_transaction():
            context.run_migrations()


run_migrations()