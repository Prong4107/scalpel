from __future__ import annotations

import urllib.parse
from typing import Iterable, Literal, cast, Sequence, Protocol, Any, TypeVar, Type
from copy import deepcopy
from pyscalpel.java.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.java.burp.http_service import IHttpService, HttpService
from pyscalpel.burp_utils import get_bytes
from pyscalpel.java.burp.byte_array import IByteArray
from pyscalpel.java.scalpel_types.utils import PythonUtils
from pyscalpel.encoding import always_bytes, always_str
from pyscalpel.http.headers import Headers
from mitmproxy.coretypes import multidict
from mitmproxy.net.http.url import (
    parse as url_parse,
    unparse as url_unparse,
    encode as url_encode,
    decode as url_decode,
)

from pyscalpel.http.body import (
    Serializer,
    JSONFormSerializer,
    URLEncodedFormSerializer,
    OctetStreamSerializer,
    QueryParamsView,
    QueryParams,
    JSON_KEY_TYPES,
    JSON_VALUE_TYPES,
    CONTENT_TYPE_TO_SERIALIZER,
)


class Request:
    """A wrapper class for `mitmproxy.http.Request`.

    This class provides additional methods for converting requests between Burp suite and MITMProxy formats.
    """

    _Port = int
    _QueryParam = tuple[str, str]
    _ParsedQuery = list[_QueryParam]
    _HttpVersion = str
    _HeaderKey = str
    _HeaderValue = str
    _Header = tuple[_HeaderKey, _HeaderValue]
    _Headers = list[_Header]
    _Host = str
    _Method = str
    _Scheme = str
    _Authority = str
    _Content = bytes
    _Path = str

    host: _Host
    port: _Port
    method: _Method
    scheme: _Scheme
    authority: _Authority

    # Path also includes URI parameters (;), query (?) and fragment (#)
    # Simply because it is more conveninent to manipulate that way in a pentensting context
    # It also mimics the way mitmproxy works.
    path: _Path

    http_version: _HttpVersion
    headers: Headers
    _content: _Content | None
    _serializer: Serializer = OctetStreamSerializer()
    _deserialized_content: Any = None
    _old_deserialized_content: Any = None
    _is_form_initialized: bool = False

    def __init__(
        self,
        method: str,
        scheme: str,
        host: str,
        port: int,
        path: str,
        http_version: str,
        headers: Headers
        | tuple[tuple[bytes, bytes], ...]
        | Iterable[tuple[bytes, bytes]],
        authority: str,
        content: bytes | None,
    ):
        self.scheme = scheme
        self.host = host
        self.port = port
        self.path = path
        self.method = method
        self.authority = authority
        self.http_version = http_version
        self.headers = headers if isinstance(headers, Headers) else Headers(headers)
        self._content = content

        match self.headers.get("content-type"):
            case "application/x-www-form-urlencoded":
                self._serializer = URLEncodedFormSerializer()
            case "application/json":
                self._serializer = JSONFormSerializer()
            case _:
                self._serializer = OctetStreamSerializer()

    @staticmethod
    def _parse_qs(qs: str) -> _ParsedQuery:
        return urllib.parse.parse_qsl(qs)

    @staticmethod
    def _parse_url(
        url: str,
    ) -> tuple[_Scheme, _Host, _Port, _Path]:
        return cast(tuple[str, str, int, str], url_parse(url))

    @staticmethod
    def _unparse_url(scheme: _Scheme, host: _Host, port: _Port, path: _Path) -> str:
        return url_unparse(scheme, host, port, path)

    @classmethod
    def make(
        cls,
        method: str,
        url: str,
        content: bytes | str = "",
        headers: Headers
        | dict[str | bytes, str | bytes]
        | Iterable[tuple[bytes, bytes]] = (),
    ) -> "Request":
        scalpel_headers: Headers
        match headers:
            case Headers():
                scalpel_headers = headers
            case dict():
                casted_headers = cast(dict[str | bytes, str | bytes], headers)
                scalpel_headers = Headers(
                    (
                        (always_bytes(key), always_bytes(val))
                        for key, val in casted_headers.items()
                    )
                )
            case _:
                casted_headers = cast(Iterable[tuple[bytes, bytes]], headers)
                scalpel_headers = Headers(headers)

        scheme, host, port, path = Request._parse_url(url)
        http_version = "HTTP/1.1"

        authority: str = scalpel_headers["Host"] or ""
        encoded_content = always_bytes(content)

        assert isinstance(host, str)

        return cls(
            method=method,
            scheme=scheme,
            host=host,
            port=port,
            path=path,
            http_version=http_version,
            headers=scalpel_headers,
            authority=authority,
            content=encoded_content,
        )

    @classmethod
    def from_burp(cls, request: IHttpRequest) -> Request:
        """Construct an instance of the Request class from a Burp suite HttpRequest.
        :param request: The Burp suite HttpRequest to convert.
        :return: A Request with the same data as the Burp suite HttpRequest.
        """
        srv: IHttpService = request.httpService()
        body = get_bytes(request.body())
        # Burp will give you lowercased and pseudo headers when using HTTP/2.
        # https://portswigger.net/burp/documentation/desktop/http2/http2-normalization-in-the-message-editor#sending-requests-without-any-normalization:~:text=are%20converted%20to-,lowercase,-.
        # https://blog.yaakov.online/http-2-header-casing/
        headers: Headers = Headers.from_burp(request.headers())

        # request.url() gives a relative url for some reason
        # So we have to parse and unparse to get the full path (path + parameters + query + fragment)
        _, _, path, parameters, query, fragment = urllib.parse.urlparse(request.url())

        # Concatenate the path components
        # Empty parameters,query and fragment are lost in the process
        # e.g.: http://example.com;?# becomes http://example.com
        # To use such an URL, the user must set the path directly
        # To fix this we would need to write our own URL parser, which is a bit overkill for now.
        path = urllib.parse.urlunparse(("", "", path, parameters, query, fragment))

        host = ""
        port = 0
        scheme = "http"
        if srv is not None:
            host = srv.host()
            port = srv.port()
            scheme = "https" if srv.secure else "http"

        return cls(
            method=request.method(),
            scheme=scheme,
            host=host,
            port=port,
            path=path,
            http_version=request.httpVersion(),
            headers=headers,
            authority=headers.get(":authority") or headers.get("Host") or "",
            content=body,
        )

    def __bytes__(self) -> bytes:
        """Convert the request to bytes
        :return: The request as bytes.
        """
        # Reserialize the request to bytes.
        first_line = (
            b" ".join(
                always_bytes(s) for s in (self.method, self.path, self.http_version)
            )
            + b"\r\n"
        )

        # Strip HTTP/2 pseudo headers.
        # https://portswigger.net/burp/documentation/desktop/http2/http2-basics-for-burp-users#:~:text=HTTP/2%20specification.-,Pseudo%2Dheaders,-In%20HTTP/2
        mapped_headers = tuple(
            x for x in self.headers.fields if not x[0].startswith(b":")
        )

        if self.headers.get(b"Host") is None and self.http_version == "HTTP/2":
            # Host header is not present in HTTP/2, but is required by Burp message editor.
            # So we have to add it back from the :authority pseudo-header.
            # https://portswigger.net/burp/documentation/desktop/http2/http2-normalization-in-the-message-editor#sending-requests-without-any-normalization:~:text=pseudo%2Dheaders%20and-,derives,-the%20%3Aauthority%20from
            mapped_headers = (
                (b"host", always_bytes(self.headers[":authority"])),
            ) + tuple(mapped_headers)

        # Construct the request's headers part.
        headers_lines = b"".join(
            b"%s: %s\r\n" % (key, val) for key, val in mapped_headers
        )

        # Set a default value for the request's body. (None -> b"")
        body = self.content or b""

        # Construct the whole request and return it.
        return first_line + headers_lines + b"\r\n" + body

    def to_burp(self) -> IHttpRequest:
        """Convert the request to a Burp suite :class:`IHttpRequest`.
        :return: The request as a Burp suite :class:`IHttpRequest`.
        """
        # Convert the request to a Burp ByteArray.
        request_byte_array: IByteArray = PythonUtils.toByteArray(bytes(self))

        if self.port == 0:
            # No networking information is available, so we build a plain network-less request.
            return HttpRequest.httpRequest(request_byte_array)

        # Build the Burp HTTP networking service.
        service: IHttpService = HttpService.httpService(
            self.host, self.port, self.scheme == "https"
        )

        # Instantiate and return a new Burp HTTP request.
        return HttpRequest.httpRequest(service, request_byte_array)

    @classmethod
    def from_raw(
        cls,
        data: bytes | str,
        real_host: str = "",
        port: int = 0,
        scheme: Literal["http"] | Literal["https"] | str = "http",
    ) -> "Request":
        """Construct an instance of the Request class from raw bytes.
        :param data: The raw bytes to convert.
        :param real_host: The real host to connect to.
        :param port: The port of the request.
        :param scheme: The scheme of the request.
        :return: A :class:`Request` with the same data as the raw bytes.
        """
        # Convert the raw bytes to a Burp ByteArray.
        # We use the Burp API to trivialize the parsing of the request from raw bytes.
        str_or_byte_array: IByteArray | str = (
            data if isinstance(data, str) else PythonUtils.toByteArray(data)
        )

        # Handle the case where the networking informations are not provided.
        if port == 0:
            # Instantiate and return a new Burp HTTP request without networking informations.
            burp_request: IHttpRequest = HttpRequest.httpRequest(str_or_byte_array)
        else:
            # Build the Burp HTTP networking service.
            service: IHttpService = HttpService.httpService(
                real_host, port, scheme == "https"
            )

            # Instantiate a new Burp HTTP request with networking informations.
            burp_request: IHttpRequest = HttpRequest.httpRequest(
                service, str_or_byte_array
            )

        # Construct the request from the Burp.
        return cls.from_burp(burp_request)

    @property
    def url(self) -> str:
        """
        The full URL string, constructed from `Request.scheme`, `Request.host`, `Request.port` and `Request.path`.

        Settings this property updates these attributes as well.
        """
        return Request._unparse_url(self.scheme, self.host, self.port, self.path)

    @url.setter
    def url(self, val: str | bytes) -> None:
        (self.scheme, self.host, self.port, self.path) = Request._parse_url(
            always_str(val)
        )

    def _get_query(self) -> _ParsedQuery:
        query = urllib.parse.urlparse(self.url).query
        return url_decode(query)

    def _set_query(self, query_data: Sequence[_QueryParam]):
        query = url_encode(query_data)
        _, _, path, params, _, fragment = urllib.parse.urlparse(self.url)
        self.path = urllib.parse.urlunparse(["", "", path, params, query, fragment])

    @property
    def query(self) -> QueryParamsView:
        return QueryParamsView(
            multidict.MultiDictView(self._get_query, self._set_query)
        )

    @query.setter
    def query(self, value: Sequence[tuple[str, str]]):
        self._set_query(value)

    def _has_deserialized_content_changed(self) -> bool:
        return self._deserialized_content != self._old_deserialized_content

    def _serialize_content(self):
        self._update_serialized_content(
            self._serializer.serialize(self._deserialized_content, req=self)
        )

    def _update_serialized_content(self, serialized: bytes | None):
        self._deserialized_content = self._serializer.deserialize(serialized)
        self._old_deserialized_content = deepcopy(self._deserialized_content)
        self._content = serialized

    def _deserialize_content(self):
        if self._content:
            self._deserialized_content = self._serializer.deserialize(
                self._content, req=self
            )

    def _update_deserialized_content(self, deserialized: Any):
        if deserialized is None:
            self._deserialized_content = None
            self._old_deserialized_content = None
            self._content = None
            return

        self._deserialized_content = deserialized
        self._content = self._serializer.serialize(deserialized)

    @property
    def content(self) -> bytes | None:
        if self._has_deserialized_content_changed():
            self._update_deserialized_content(self._deserialized_content)
            self._old_deserialized_content = deepcopy(self._deserialized_content)

        return self._content

    @content.setter
    def content(self, value: bytes | str | None):
        if isinstance(value, str):
            value = value.encode()
        self._update_serialized_content(value)

    def update_form_content_type(self, content_type: str | None = None):
        _content_type: str = content_type or self.headers.get("Content-Type") or ""

        serializer = CONTENT_TYPE_TO_SERIALIZER.get(_content_type, self._serializer)
        self._set_serializer(serializer)

    @property
    def form(self) -> Any:
        return self._deserialized_content

    @form.setter
    def form(self, form: Any):
        self._deserialized_content = form

        # Update raw _content
        self._serialize_content()

    def _set_serializer(self, serializer: Serializer):
        self._serialize_content()
        self._serializer = serializer
        self._update_serialized_content(self._content)

    def _update_serializer_and_get_form(self, serializer: Serializer) -> Any:
        self._set_serializer(serializer)

        return self._deserialized_content

    def _update_serializer_and_set_form(self, serializer: Serializer, form: Any) -> Any:
        self._set_serializer(serializer)

        self._update_deserialized_content(form)

    @property
    def urlencoded_form(self) -> QueryParams:
        return self._update_serializer_and_get_form(URLEncodedFormSerializer())

    @urlencoded_form.setter
    def urlencoded_form(self, form: QueryParams):
        self._update_serializer_and_set_form(JSONFormSerializer(), form)

    @property
    def json_form(self) -> dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]:
        return self._update_serializer_and_get_form(JSONFormSerializer())

    @json_form.setter
    def json_form(self, form: dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]):
        self._update_serializer_and_set_form(JSONFormSerializer(), form)

    @property
    def raw_form(self) -> bytes:
        return self._update_serializer_and_get_form(OctetStreamSerializer())

    @raw_form.setter
    def raw_form(self, form: bytes):
        self._update_serializer_and_set_form(OctetStreamSerializer(), form)
