from typing import Iterable
import time
from collections.abc import Iterable
from mitmproxy.utils import strutils
from pyscalpel.java.burp.http_header import IHttpHeader, HttpHeader
from pyscalpel.java.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.java.burp.http_response import IHttpResponse, HttpResponse
from mitmproxy.http import (
    Headers as MITMProxyHeaders,
    Request as MITMProxyRequest,
    Response as MITMProxyResponse,
)
from functools import singledispatchmethod
from .java.burp.http_service import IHttpService, HttpService
from .burp_utils import get_bytes
from .java.burp.byte_array import IByteArray
from .java.scalpel_types.utils import PythonUtils
from typing import NoReturn
import time


def _always_bytes(x: str | bytes) -> bytes:
    return strutils.always_bytes(x, "utf-8", "surrogateescape")


def _native(x: bytes) -> str:
    # While headers _should_ be ASCII, it's not uncommon for certain headers to be utf-8 encoded.
    return x.decode("utf-8", "surrogateescape")


class Headers(MITMProxyHeaders):
    def __init__(self, fields: Iterable[tuple[bytes, bytes]] = ..., **headers):
        super().__init__(fields, **headers)

    @classmethod
    def from_mitmproxy(cls, headers: MITMProxyHeaders) -> "Headers":
        return cls(((_always_bytes(header[0]), _always_bytes(header[1])) for header in headers.fields))

    @classmethod
    def from_burp(cls, headers: list[IHttpHeader]) -> "Headers":
        return cls(((_always_bytes(header.name()), _always_bytes(header.value())) for header in headers))

    def to_burp(self) -> list[IHttpHeader]:
        return [HttpHeader.httpHeader(header[0], header[1]) for header in self.fields]


class Request(MITMProxyRequest):
    def __init__(
        self,
        host: str,
        port: int,
        method: bytes,
        scheme: bytes,
        authority: bytes,
        path: bytes,
        http_version: bytes,
        headers: Headers | tuple[tuple[bytes, bytes], ...],
        content: bytes | None,
        trailers: Headers | tuple[tuple[bytes, bytes], ...] | None,
    ):
        super().__init__(
            host,
            port,
            method,
            scheme,
            authority,
            path,
            http_version,
            headers,
            content,
            trailers,
            timestamp_start=time.time(),
            timestamp_end=time.time(),
        )

    @classmethod
    def make(
        cls,
        method: str,
        url: str,
        content: bytes | str = "",
        headers: Headers | dict[str | bytes, str | bytes] | Iterable[tuple[bytes, bytes]] = (),
    ) -> "Request":
        return cls.from_mitmproxy(super().make(method, url, content, headers))

    @classmethod
    def from_mitmproxy(cls, request: MITMProxyRequest) -> "Request":
        return cls(
            request.host,
            request.port,
            _always_bytes(request.method),
            _always_bytes(request.scheme),
            _always_bytes(request.authority),
            _always_bytes(request.path),
            _always_bytes(request.http_version),
            Headers.from_mitmproxy(request.headers),
            request.content,
            Headers.from_mitmproxy(request.trailers) if request.trailers else None,
        )

    @classmethod
    def from_burp(cls, request: IHttpRequest) -> "Request":
        srv: IHttpService = request.httpService()
        body = get_bytes(request.body())
        if srv is None:
            return cls(
                "",
                0,
                _always_bytes(request.method()),
                b"",
                b"",
                _always_bytes(request.path()),
                b"HTTP/1.1",
                Headers.from_burp(request.headers()),
                body,
                None,
            )

        return cls(
            srv.host(),
            srv.port(),
            _always_bytes(request.method()),
            b"https" if srv.secure() else b"http",
            _always_bytes(srv.host()),
            _always_bytes(request.path()),
            b"HTTP/1.1",
            Headers.from_burp(request.headers()),
            body,
            None,
        )

    def to_bytes(self) -> bytes:
        # Reserialize the request to bytes.
        first_line = b" ".join(_always_bytes(s) for s in (self.method, self.path, self.http_version)) + b"\r\n"
        # TODO: Check if the use of '% formatting' cause encoding issues.
        headers_lines = b"".join(b"%s: %s\r\n" % (key, val) for key, val in self.headers.fields)
        body = self.content or b""
        return first_line + headers_lines + b"\r\n" + body

    def to_burp(self) -> IHttpRequest:
        service: IHttpService = HttpService.httpService(self.host, self.port, self.scheme == b"https")
        request_byte_array: IByteArray = PythonUtils.toByteArray(self.to_bytes())
        return HttpRequest.httpRequest(service, request_byte_array)

    @classmethod
    def from_bytes(cls, data: bytes) -> "Request":
        req_byte_array: IByteArray = PythonUtils.toByteArray(data)
        burp_request: IHttpRequest = HttpRequest.httpRequest(req_byte_array)
        return cls.from_burp(burp_request)


class Response(MITMProxyResponse):
    def __init__(
        self,
        http_version: bytes,
        status_code: int,
        reason: bytes,
        headers: Headers | tuple[tuple[bytes, bytes], ...],
        content: bytes | None,
        trailers: Headers | tuple[tuple[bytes, bytes], ...] | None,
    ):
        super().__init__(
            http_version,
            status_code,
            reason,
            headers,
            content,
            trailers,
            timestamp_start=time.time(),
            timestamp_end=time.time(),
        )

    @singledispatchmethod
    @classmethod
    def from_any(cls, none: NoReturn) -> "Response":
        # Throw unsupported type exception
        raise TypeError(f"Unsupported type {type(none)}")

    @classmethod
    @from_any.register(MITMProxyResponse)
    def from_mitmproxy(cls, response: MITMProxyResponse) -> "Response":
        return cls(
            _always_bytes(response.http_version),
            response.status_code,
            _always_bytes(response.reason),
            Headers.from_mitmproxy(response.headers),
            response.content,
            Headers.from_mitmproxy(response.trailers) if response.trailers else None,
        )

    @classmethod
    @from_any.register(IHttpResponse)
    def from_burp(cls, response: IHttpResponse) -> "Response":
        body = get_bytes(response.body())
        return cls(
            b"HTTP/1.1",
            response.statusCode(),
            _always_bytes(response.reasonPhrase()),
            Headers.from_burp(response.headers()),
            body,
            None,
        )

    def to_bytes(self) -> bytes:
        # Reserialize the response to bytes.
        first_line = (
            b" ".join(_always_bytes(s) for s in (self.http_version, str(self.status_code), self.reason)) + b"\r\n"
        )
        headers_lines = b"".join(b"%s: %s\r\n" % (key, val) for key, val in self.headers.fields)
        body = self.content or b""
        return first_line + headers_lines + b"\r\n" + body

    def to_burp(self) -> IHttpResponse:
        response_byte_array: IByteArray = PythonUtils.toByteArray(self.to_bytes())
        return HttpResponse.httpResponse(response_byte_array)

    @classmethod
    @from_any.register(bytes)
    def from_bytes(cls, data: bytes) -> "Response":
        resp_byte_array: IByteArray = PythonUtils.toByteArray(data)
        burp_response: IHttpResponse = HttpResponse.httpResponse(resp_byte_array)
        return cls.from_burp(burp_response)

    @classmethod
    def make(
        cls,
        status_code: int = 200,
        content: bytes | None = b"",
        headers: Headers | tuple[tuple[bytes, bytes], ...] = (),
    ) -> "Response":
        mitmproxy_res = cls.make(status_code, content, headers)
        return cls.from_mitmproxy(mitmproxy_res)
