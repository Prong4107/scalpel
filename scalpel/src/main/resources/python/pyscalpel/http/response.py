import time

from functools import lru_cache
from mitmproxy.http import (
    Response as MITMProxyResponse,
)


from pyscalpel.java.burp.http_response import IHttpResponse, HttpResponse
from pyscalpel.burp_utils import get_bytes
from pyscalpel.java.burp.byte_array import IByteArray
from pyscalpel.java.scalpel_types.utils import PythonUtils
from pyscalpel.encoding import always_bytes
from pyscalpel.http.headers import Headers


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
        # Construct the base/inherited MITMProxy response.
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
    # https://docs.mitmproxy.org/stable/api/mitmproxy/http.html#Response
    # link to mitmproxy documentation
    def from_mitmproxy(cls, response: MITMProxyResponse) -> "Response":
        """Construct an instance of the Response class from a [mitmproxy.http.HTTPResponse](https://docs.mitmproxy.org/stable/api/mitmproxy/http.html#Response).
        :param response: The [mitmproxy.http.HTTPResponse](https://docs.mitmproxy.org/stable/api/mitmproxy/http.html#Response) to convert.
        :return: A :class:`Response` with the same data as the [mitmproxy.http.HTTPResponse](https://docs.mitmproxy.org/stable/api/mitmproxy/http.html#Response).
        """
        return cls(
            always_bytes(response.http_version),
            response.status_code,
            always_bytes(response.reason),
            Headers.from_mitmproxy(response.headers),
            response.content,
            Headers.from_mitmproxy(response.trailers) if response.trailers else None,
        )

    @classmethod
    def from_burp(cls, response: IHttpResponse) -> "Response":
        """Construct an instance of the Response class from a Burp suite :class:`IHttpResponse`."""
        body = get_bytes(response.body())
        return cls(
            always_bytes(response.httpVersion()),
            response.statusCode(),
            always_bytes(response.reasonPhrase()),
            Headers.from_burp(response.headers()),
            body,
            None,
        )

    @lru_cache
    def __bytes__(self) -> bytes:
        """Convert the response to raw bytes."""
        # Reserialize the response to bytes.

        # Format the first line of the response. (e.g. "HTTP/1.1 200 OK\r\n")
        first_line = (
            b" ".join(
                always_bytes(s)
                for s in (self.http_version, str(self.status_code), self.reason)
            )
            + b"\r\n"
        )

        # Format the response's headers part.
        headers_lines = b"".join(
            b"%s: %s\r\n" % (key, val) for key, val in self.headers.fields
        )

        # Set a default value for the response's body. (None -> b"")
        body = self.content or b""

        # Build the whole response and return it.
        return first_line + headers_lines + b"\r\n" + body

    @lru_cache
    def to_burp(self) -> IHttpResponse:
        """Convert the response to a Burp suite :class:`IHttpResponse`."""
        # Convert the response to a Burp ByteArray.
        response_byte_array: IByteArray = PythonUtils.toByteArray(bytes(self))

        # Instantiate and return a new Burp HTTP response.
        return HttpResponse.httpResponse(response_byte_array)

    @classmethod
    def from_raw(cls, data: bytes | str) -> "Response":
        """Construct an instance of the Response class from raw bytes.
        :param data: The raw bytes to convert.
        :return: A :class:`Response` parsed from the raw bytes.
        """
        # Use the Burp API to trivialize the parsing of the response from raw bytes.
        # Convert the raw bytes to a Burp ByteArray.
        # Plain strings are OK too.
        str_or_byte_array: IByteArray | str = (
            data if isinstance(data, str) else PythonUtils.toByteArray(data)
        )

        # Instantiate a new Burp HTTP response.
        burp_response: IHttpResponse = HttpResponse.httpResponse(str_or_byte_array)

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
