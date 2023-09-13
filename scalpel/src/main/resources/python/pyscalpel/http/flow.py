from __future__ import annotations
from dataclasses import dataclass
from typing import Literal
from pyscalpel.http.request import Request
from pyscalpel.http.response import Response
from pyscalpel.http.utils import host_is


@dataclass(frozen=True)
class Flow:
    """Contains request and response and some utilities for match()"""

    scheme: Literal["http", "https"] = "http"
    host: str = ""
    port: int = 0
    request: Request | None = None
    response: Response | None = None
    text: bytes | None = None

    def host_is(self, *patterns: str) -> bool:
        """Matches a wildcard pattern againt the target host

        Returns:
            bool: True if at least one pattern matched
        """
        return host_is(self.host, *patterns)

    def path_is(self, *patterns: str) -> bool:
        """Matches a wildcard pattern againt the request path

        Includes query string `?` and fragment `#`

        Returns:
            bool: True if at least one pattern matched
        """
        req = self.request
        if req is None:
            return False

        return req.path_is(*patterns)
