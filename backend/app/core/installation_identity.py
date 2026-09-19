import re
from hashlib import sha256
from typing import Annotated

from fastapi import Depends, Header, HTTPException


INSTALLATION_TOKEN_PATTERN = re.compile(
    r"Bearer [0-9a-f]{64}"
)


def installation_owner_hash(
        authorization: Annotated[
            str | None,
            Header()
        ] = None
) -> str:
    if (
            authorization is None
            or INSTALLATION_TOKEN_PATTERN.fullmatch(
        authorization
    ) is None
    ):
        raise HTTPException(
            status_code=401,
            detail=(
                "Identificación de instalación requerida."
            )
        )

    installation_token = authorization.removeprefix(
        "Bearer "
    )

    return sha256(
        installation_token.encode("utf-8")
    ).hexdigest()


OwnerHash = Annotated[
    str,
    Depends(installation_owner_hash)
]