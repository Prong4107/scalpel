from __future__ import annotations

from typing import (
    Iterable,
)
from mitmproxy.http import (
    Headers as MITMProxyHeaders,
)


from pyscalpel.java.burp.http_header import IHttpHeader, HttpHeader
from pyscalpel.encoding import always_bytes, native


class Headers(MITMProxyHeaders):
    """A wrapper around the MITMProxy Headers.

    This class provides additional methods for converting headers between Burp suite and MITMProxy formats.
    """

    def __init__(self, fields: Iterable[tuple[bytes, bytes]] = ..., **headers):
        """
        :param fields: The headers to construct the from.
        :param headers: The headers to construct the from.
        """

        # Construct the base/inherited MITMProxy headers.
        super().__init__(fields, **headers)

    @classmethod
    def from_mitmproxy(cls, headers: MITMProxyHeaders) -> Headers:
        """
        Creates a `Headers` from a `mitmproxy.http.Headers`.

        :param headers: The `mitmproxy.http.Headers` to convert.
        :type headers: :class Headers <https://docs.mitmproxy.org/stable/api/mitmproxy/http.html#Headers>`
        :return: A `Headers` with the same headers as the `mitmproxy.http.Headers`.
        """

        # Construct from the raw MITMProxy headers data.
        return cls(headers.fields)

    @classmethod
    def from_burp(cls, headers: list[IHttpHeader]) -> Headers:
        """Construct an instance of the Headers class from a Burp suite HttpHeader array.
        :param headers: The Burp suite HttpHeader array to convert.
        :return: A Headers with the same headers as the Burp suite HttpHeader array.
        """

        # print(f"burp: {headers}")
        # Convert the list of Burp IHttpHeaders to a list of tuples: (key, value)
        return cls(
            (
                (always_bytes(header.name()), always_bytes(header.value()))
                for header in headers
            )
        )

    def to_burp(self) -> list[IHttpHeader]:
        """Convert the headers to a Burp suite HttpHeader array.
        :return: A Burp suite HttpHeader array with the same headers as the Headers.
        """

        # Convert the list of tuples: (key, value) to a list of Burp IHttpHeaders
        return [
            HttpHeader.httpHeader(native(header[0]), native(header[1]))
            for header in self.fields
        ]
