from __future__ import annotations
from dataclasses import dataclass
from typing import Literal
from pyscalpel.http.request import Request
from pyscalpel.http.response import Response


@dataclass(frozen=True)
class Flow:
    scheme: Literal["http", "https"] = "http"
    host: str = ""
    port: int = 0
    request: Request | None = None
    response: Response | None = None
    text: bytes | None = None

