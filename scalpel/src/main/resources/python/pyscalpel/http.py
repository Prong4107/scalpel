import time

from typing import Iterable, NoReturn, Literal

from mitmproxy.utils import strutils
from mitmproxy.http import (
    Headers as MITMProxyHeaders,
    Request as MITMProxyRequest,
    Response as MITMProxyResponse,
)

from pyscalpel.java.burp.http_header import IHttpHeader, HttpHeader
from pyscalpel.java.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.java.burp.http_response import IHttpResponse, HttpResponse
from pyscalpel.java.burp.http_service import IHttpService, HttpService
from pyscalpel.burp_utils import get_bytes
from pyscalpel.java.burp.byte_array import IByteArray
from pyscalpel.java.scalpel_types.utils import PythonUtils


# str/bytes conversion helpers from mitmproxy/http.py:
# https://github.com/mitmproxy/mitmproxy/blob/main/mitmproxy/http.py#:~:text=def-,_native,-(x%3A
def _always_bytes(x: str | bytes) -> bytes:
    return strutils.always_bytes(x, "utf-8", "surrogateescape")


def _native(x: bytes) -> str:
    # While headers _should_ be ASCII, it's not uncommon for certain headers to be utf-8 encoded.
    return x.decode("utf-8", "surrogateescape")


class Headers(MITMProxyHeaders):
    def __init__(self, fields: Iterable[tuple[bytes, bytes]] = ..., **headers):
        # Construct the base/inherited MITMProxy headers object.
        super().__init__(fields, **headers)

    @classmethod
    def from_mitmproxy(cls, headers: MITMProxyHeaders) -> "Headers":
        # Construct from the raw MITMProxy headers data.
        return cls(headers.fields)
        # return cls(((_always_bytes(header[0]), _always_bytes(header[1])) for header in headers.fields))

    @classmethod
    def from_burp(cls, headers: list[IHttpHeader]) -> "Headers":
        # Convert the list of Burp IHttpHeader objects to a list of tuples: (key, value)
        return cls(((_always_bytes(header.name()), _always_bytes(header.value())) for header in headers))

    def to_burp(self) -> list[IHttpHeader]:
        # Convert the list of tuples: (key, value) to a list of Burp IHttpHeader objects
        return [HttpHeader.httpHeader(_native(header[0]), _native(header[1])) for header in self.fields]


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
        # Construct the base/inherited MITMProxy request object.
        # Burp does not provide timestamps for requests, so we set them to the current time as a default value like mitmproxy when using Request.make()
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
        # Simply call the inherited make() method and then construct from the resulting MITMProxy request object.
        return cls.from_mitmproxy(super().make(method, url, content, headers))

    @classmethod
    def from_mitmproxy(cls, request: MITMProxyRequest) -> "Request":
        # Convert every field to the required type.
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
        # Burp will give you lowercased and pseudo headers when using HTTP/2.
        # https://portswigger.net/burp/documentation/desktop/http2/http2-normalization-in-the-message-editor#sending-requests-without-any-normalization:~:text=are%20converted%20to-,lowercase,-.
        # https://blog.yaakov.online/http-2-header-casing/
        headers: Headers = Headers.from_burp(request.headers())

        # Check if the request has networking informations.
        if srv is None:
            # Requests from Burp message editor usually do not have any networking information from the original request,
            # so in this case we replace the missing values to the default values MITMProxy uses.
            req = cls(
                "",
                0,
                _always_bytes(request.method()),
                b"",
                b"",
                _always_bytes(request.path()),
                _always_bytes(request.httpVersion()),
                headers,
                body,
                None,
            )
        else:
            # Construct the request from the networking informations Burp provided.
            req = cls(
                srv.host(),
                srv.port(),
                _always_bytes(request.method()),
                b"https" if srv.secure() else b"http",
                _always_bytes(srv.host()),
                _always_bytes(request.path()),
                _always_bytes(request.httpVersion()),
                headers,
                body,
                None,
            )

        return req

    def to_bytes(self) -> bytes:
        # Reserialize the request to bytes.
        first_line = b" ".join(_always_bytes(s) for s in (self.method, self.path, self.http_version)) + b"\r\n"

        # Strip HTTP/2 pseudo headers.
        # https://portswigger.net/burp/documentation/desktop/http2/http2-basics-for-burp-users#:~:text=HTTP/2%20specification.-,Pseudo%2Dheaders,-In%20HTTP/2
        mapped_headers = tuple(x for x in self.headers.fields if not x[0].startswith(b":"))

        if self.headers.get(b"Host") is None and self.http_version == "HTTP/2":
            # Host header is not present in HTTP/2, but is required by Burp message editor.
            # So we have to add it back from the :authority pseudo-header.
            # https://portswigger.net/burp/documentation/desktop/http2/http2-normalization-in-the-message-editor#sending-requests-without-any-normalization:~:text=pseudo%2Dheaders%20and-,derives,-the%20%3Aauthority%20from
            mapped_headers = ((b"host", _always_bytes(self.headers[":authority"])),) + tuple(mapped_headers)

        # Construct the request's headers part.
        headers_lines = b"".join(b"%s: %s\r\n" % (key, val) for key, val in mapped_headers)

        # Set a default value for the request's body. (None -> b"")
        body = self.content or b""

        # Construct the whole request and return it.
        return first_line + headers_lines + b"\r\n" + body

    def to_burp(self) -> IHttpRequest:
        # Build the Burp HTTP networking service.
        service: IHttpService = HttpService.httpService(self.host, self.port, self.scheme == "https")

        # Convert the request to a Burp ByteArray.
        request_byte_array: IByteArray = PythonUtils.toByteArray(self.to_bytes())

        # Instantiate and return a new Burp HTTP request.
        return HttpRequest.httpRequest(service, request_byte_array)

    @classmethod
    def from_bytes(
        cls,
        data: bytes,
        real_host: str = "",
        port: int = 0,
        scheme: Literal["http"] | Literal["https"] | str = "http",
    ) -> "Request":
        # Convert the raw bytes to a Burp ByteArray.
        # We use the Burp API to trivialize the parsing of the request from raw bytes.
        req_byte_array: IByteArray = PythonUtils.toByteArray(data)

        # Handle the case where the networking informations are not provided.
        if port == 0:
            # Instantiate and return a new Burp HTTP request without networking informations.
            burp_request: IHttpRequest = HttpRequest.httpRequest(req_byte_array)
        else:
            # Build the Burp HTTP networking service.
            service: IHttpService = HttpService.httpService(real_host, port, scheme == "https")

            # Instantiate a new Burp HTTP request with networking informations.
            burp_request: IHttpRequest = HttpRequest.httpRequest(service, req_byte_array)

        # Construct the request from the Burp object.
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
        # Construct the base/inherited MITMProxy response object.
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

    @classmethod
    def from_any(cls, none: NoReturn) -> "Response":
        # This should never be called because the call should be dispatched to the correct implementation.
        raise TypeError(f"Unsupported type {type(none)}")

    @classmethod
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
    def from_burp(cls, response: IHttpResponse) -> "Response":
        body = get_bytes(response.body())
        return cls(
            _always_bytes(response.httpVersion()),
            response.statusCode(),
            _always_bytes(response.reasonPhrase()),
            Headers.from_burp(response.headers()),
            body,
            None,
        )

    def to_bytes(self) -> bytes:
        # Reserialize the response to bytes.

        # Format the first line of the response. (e.g. "HTTP/1.1 200 OK\r\n")
        first_line = (
            b" ".join(_always_bytes(s) for s in (self.http_version, str(self.status_code), self.reason)) + b"\r\n"
        )

        # Format the response's headers part.
        headers_lines = b"".join(b"%s: %s\r\n" % (key, val) for key, val in self.headers.fields)

        # Set a default value for the response's body. (None -> b"")
        body = self.content or b""

        # Build the whole response and return it.
        return first_line + headers_lines + b"\r\n" + body

    def to_burp(self) -> IHttpResponse:
        # Convert the response to a Burp ByteArray.
        response_byte_array: IByteArray = PythonUtils.toByteArray(self.to_bytes())

        # Instantiate and return a new Burp HTTP response.
        return HttpResponse.httpResponse(response_byte_array)

    @classmethod
    def from_bytes(cls, data: bytes) -> "Response":
        # Use the Burp API to trivialize the parsing of the response from raw bytes.
        # Convert the raw bytes to a Burp ByteArray.
        resp_byte_array: IByteArray = PythonUtils.toByteArray(data)

        # Instantiate a new Burp HTTP response.
        burp_response: IHttpResponse = HttpResponse.httpResponse(resp_byte_array)

        return cls.from_burp(burp_response)

    @classmethod
    def make(
        cls,
        status_code: int = 200,
        content: bytes | None = b"",
        headers: Headers | tuple[tuple[bytes, bytes], ...] = (),
    ) -> "Response":
        # Use the base/inherited make method to construct a MITMProxy response.
        mitmproxy_res = cls.make(status_code, content, headers)

        return cls.from_mitmproxy(mitmproxy_res)
