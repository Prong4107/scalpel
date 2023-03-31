from typing import Protocol, List, Iterable
from abc import abstractmethod, ABCMeta
import binascii
import json
import os
import re
import time
import urllib.parse
import warnings
from collections.abc import Callable
from collections.abc import Iterable
from collections.abc import Iterator
from collections.abc import Mapping
from collections.abc import Sequence
from dataclasses import dataclass
from email.utils import formatdate
from email.utils import mktime_tz
from email.utils import parsedate_tz
from typing import Any
from typing import cast
from mitmproxy import flow
from mitmproxy.coretypes import multidict
from mitmproxy.coretypes import serializable
from mitmproxy.net import encoding
from mitmproxy.net.http import cookies
from mitmproxy.net.http import multipart
from mitmproxy.net.http import status_codes
from mitmproxy.net.http import url
from mitmproxy.net.http.headers import assemble_content_type
from mitmproxy.net.http.headers import parse_content_type
from mitmproxy.utils import human
from mitmproxy.utils import strutils
from mitmproxy.utils import typecheck
from mitmproxy.utils.strutils import always_bytes
from mitmproxy.utils.strutils import always_str
from mitmproxy.websocket import WebSocketData
from pyscalpel.java.burp.http_header import IHttpHeader, HttpHeader


# from: https://github.com/mitmproxy/mitmproxy/blob/51670861e6f8c11e8309804cfe15d2599a05aee7/mitmproxy/http.py#:~:text=def%20_always_bytes(,%22surrogateescape%22)
def _always_bytes(x: str | bytes) -> bytes:
    return strutils.always_bytes(x, "utf-8", "surrogateescape")

# While headers _should_ be ASCII, it's not uncommon for certain headers to be utf-8 encoded.


def _native(x: bytes) -> str:
    return x.decode("utf-8", "surrogateescape")


class Headers(multidict.MultiDict, metaclass=ABCMeta):
    """

    Based on : https://github.com/mitmproxy/mitmproxy/blob/main/mitmproxy/http.py

    Header class which allows both convenient access to individual headers as well as
    direct access to the underlying raw data. Provides a full dictionary interface.
    Create headers with keyword arguments:
    >>> h = Headers(host="example.com", content_type="application/xml")
    Headers mostly behave like a normal dict:
    >>> h["Host"]
    "example.com"
    Headers are case insensitive:
    >>> h["host"]
    "example.com"
    Headers can also be created from a list of raw (header_name, header_value) byte tuples:
    >>> h = Headers([
        (b"Host",b"example.com"),
        (b"Accept",b"text/html"),
        (b"accept",b"application/xml")
    ])
    Multiple headers are folded into a single header as per RFC 7230:
    >>> h["Accept"]
    "text/html, application/xml"
    Setting a header removes all existing headers with the same name:
    >>> h["Accept"] = "application/text"
    >>> h["Accept"]
    "application/text"
    `bytes(h)` returns an HTTP/1 header block:
    >>> print(bytes(h))
    Host: example.com
    Accept: application/text
    For full control, the raw header fields can be accessed:
    >>> h.fields
    Caveats:
     - For use with the "Set-Cookie" and "Cookie" headers, either use `Response.cookies` or see `Headers.get_all`.
    """

    def __init__(self, fields: Iterable[tuple[bytes, bytes]] = (), **headers):
        ...

    fields: tuple[tuple[bytes, bytes], ...]

    def __bytes__(self) -> bytes:
        ...

    def __delitem__(self, key: str | bytes) -> None:
        ...

    def __iter__(self) -> Iterator[str]:
        ...

    def get_all(self, key: str | bytes) -> list[str]:
        """
        Like `Headers.get`, but does not fold multiple headers into a single one.
        This is useful for Set-Cookie and Cookie headers, which do not support folding.

        *See also:*
         - <https://tools.ietf.org/html/rfc7230#section-3.2.2>
         - <https://datatracker.ietf.org/doc/html/rfc6265#section-5.4>
         - <https://datatracker.ietf.org/doc/html/rfc7540#section-8.1.2.5>
        """
        ...

    def set_all(self, key: str | bytes, values: list[str | bytes]):
        """
        Explicitly set multiple headers for the given key.
        See `Headers.get_all`.
        """

    def insert(self, index: int, key: str | bytes, value: str | bytes):
        ...

    def items(self, multi=False):
        ...

    @classmethod
    def from_burp(cls, headers: list[IHttpHeader]) -> 'Headers':
        return Headers(((_always_bytes(header.name()), _always_bytes(header.value()))
                        for header in headers))

    def to_burp(self) -> list[IHttpHeader]:
        return [HttpHeader.httpHeader(header[0], header[1]) for header in self.fields]

        ###


class Message(metaclass=ABCMeta):
    http_version: str

    headers: Headers

    # Burp doesn't handle trailers (?)

    raw_content: bytes | None

    content: bytes | None

    text: bytes | None

    def set_content(self, content: bytes | None) -> None:
        ...

    def get_content(self, strict: bool = True) -> bytes | None:
        ...

    def set_text(self, text: str | None) -> None:
        ...

    def get_text(self, strict: bool = True) -> str | None:
        ...

    def decode(self, strict: bool = True) -> None:
        ...

    def encode(self, encoding: str) -> None:
        ...

    def json(self, **kwargs: Any) -> Any:
        ...

    def copy(self) -> 'Message':
        ...


###

class Request(Message, metaclass=ABCMeta):

    def __init__(self,
                 host: str,
                 port: int,
                 method: bytes,
                 scheme: bytes,
                 authority: bytes,
                 path: bytes,
                 http_version: bytes,
                 headers: Headers | tuple[tuple[bytes,  bytes], ...],
                 content: bytes | None,
                 trailers: Headers | tuple[tuple[bytes, bytes], ...] | None):
        ...

    @ classmethod
    def make(cls,
             method: str,
             url: str,
             content: bytes | str = "",
             headers: Headers | dict[str | bytes, str |
                                     bytes] | Iterable[tuple[bytes, bytes]] = ()
             ) -> "Request":
        ...

    first_line_format: str
    method: str
    scheme: str
    authority: str
    host: str
    host_header: str
    port: int
    path: str
    url: str
    pretty_host: str
    pretty_url: str
    query: multidict.MultiDictView[str, str]
    cookies: multidict.MultiDictView[str, str]
    path_components: tuple[str, ...]

    def anticache(self) -> None:
        ...

    def anticomp(self) -> None:
        ...

    def constrain_encoding(self) -> None:
        ...

    urlencoded_form: multidict.MultiDictView[str, str]

    multipart_form: multidict.MultiDictView[bytes, bytes]


###


class Response(Message, metaclass=ABCMeta):

    def __init__(
        self,
        http_version: bytes,
        status_code: int,
        reason: bytes,
        headers: Headers | tuple[tuple[bytes, bytes], ...],
        content: bytes | None,
        trailers: Headers | tuple[tuple[bytes, bytes], ...] | None,
    ):
        ...

    status_code: int
    reason: bytes
    cookies: multidict.MultiDictView[str,
                                     tuple[str, multidict.MultiDict[str, str | None]]]

    def refresh(self, now=None) -> None: ...
