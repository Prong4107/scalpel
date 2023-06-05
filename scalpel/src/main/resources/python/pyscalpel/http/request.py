from __future__ import annotations

import urllib.parse
import re

from typing import (
    Iterable,
    Literal,
    cast,
    Sequence,
    Any,
    MutableMapping,
)
from copy import deepcopy
from pyscalpel.java.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.java.burp.http_service import IHttpService, HttpService
from pyscalpel.burp_utils import get_bytes
from pyscalpel.java.burp.byte_array import IByteArray
from pyscalpel.java.scalpel_types.utils import PythonUtils
from pyscalpel.encoding import always_bytes, always_str
from pyscalpel.http.headers import Headers
from pyscalpel.http.mime import get_header_value_without_params
from mitmproxy.coretypes import multidict
from mitmproxy.net.http.url import (
    parse as url_parse,
    unparse as url_unparse,
    encode as url_encode,
    decode as url_decode,
)
from mitmproxy.net.http import cookies


from pyscalpel.http.body import (
    FormSerializer,
    JSONFormSerializer,
    URLEncodedFormSerializer,
    MultiPartFormSerializer,
    MultiPartForm,
    MultiPartFormField,
    QueryParamsView,
    QueryParams,
    JSON_KEY_TYPES,
    JSON_VALUE_TYPES,
    CONTENT_TYPE_TO_SERIALIZER,
    JSONForm,
    IMPLEMENTED_CONTENT_TYPES,
    ImplementedContentTypesTp,
    json_escape_bytes,
    json_unescape,
    json_unescape_bytes,
)


class FormNotParsedException(Exception):
    pass


class Request:
    """A wrapper class for `mitmproxy.http.Request`.

    This class provides additional methods for converting requests between Burp suite and MITMProxy formats.
    """

    _Port = int
    _QueryParam = tuple[str, str]
    _ParsedQuery = tuple[_QueryParam]
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
    _serializer: FormSerializer | None = None
    _deserialized_content: Any = None
    _content: _Content | None
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

        self.update_serializer_from_content_type(
            self.headers.get("Content-Type"), fail_silently=True
        )

    @staticmethod
    def _parse_qs(qs: str) -> _ParsedQuery:
        return tuple(urllib.parse.parse_qsl(qs))

    @staticmethod
    def _parse_url(
        url: str,
    ) -> tuple[_Scheme, _Host, _Port, _Path]:
        scheme, host, port, path = url_parse(url)
        return cast(
            tuple[str, str, int, str],
            (scheme.decode("ascii"), host.decode("idna"), port, path.decode("ascii")),
        )

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
        | dict[str, str]
        | dict[bytes, bytes]
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

        # Inferr missing Host header from URL
        host_header = scalpel_headers.get("Host")
        if host_header is None:
            match (scheme, port):
                case ("http", 80) | ("https", 443):
                    host_header = host
                case _:
                    host_header = f"{host}:{port}"

            scalpel_headers["Host"] = host_header

        authority: str = host_header
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
        service: IHttpService = request.httpService()
        body = get_bytes(request.body())

        # Burp will give you lowercased and pseudo headers when using HTTP/2.
        # https://portswigger.net/burp/documentation/desktop/http2/http2-normalization-in-the-message-editor#sending-requests-without-any-normalization:~:text=are%20converted%20to-,lowercase,-.
        # https://blog.yaakov.online/http-2-header-casing/
        headers: Headers = Headers.from_burp(request.headers())

        # Burp gives a 0 length byte array body even when it doesn't exist, instead of null.
        # Empty but existing bodies without a Content-Length header are lost in the process.
        if not body and not headers.get("Content-Length"):
            body = None

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
        if service:
            host = service.host()
            port = service.port()
            scheme = "https" if service.secure else "http"

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
        return tuple(url_decode(query))

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
        if self._serializer is None:
            return

        # TODO: Check if this guard is still useful
        if self._deserialized_content is None:
            self._content = None
            return

        self._update_serialized_content(
            self._serializer.serialize(self._deserialized_content, req=self)
        )

    def _update_serialized_content(self, serialized: bytes):
        if self._serializer is None:
            self._content = serialized
            return

        # Update the parsed form
        self._deserialized_content = self._serializer.deserialize(serialized, self)
        self._old_deserialized_content = deepcopy(self._deserialized_content)

        # Set the raw content directly
        self._content = serialized

    def _deserialize_content(self):
        if self._serializer is None:
            return

        if self._content:
            self._deserialized_content = self._serializer.deserialize(
                self._content, req=self
            )

    def _update_deserialized_content(self, deserialized: Any):
        if self._serializer is None:
            return

        if deserialized is None:
            self._deserialized_content = None
            self._old_deserialized_content = None
            # TODO: Ensure this doesn't break anything
            # self._content = None
            return

        self._deserialized_content = deserialized
        self._content = self._serializer.serialize(deserialized, self)

    @property
    def content(self) -> bytes | None:
        if self._serializer and self._has_deserialized_content_changed():
            self._update_deserialized_content(self._deserialized_content)
            self._old_deserialized_content = deepcopy(self._deserialized_content)

        return self._content

    @content.setter
    def content(self, value: bytes | str | None):
        match value:
            case None:
                self._content = None
                self._deserialized_content = None
                return
            case str():
                value = value.encode()
        # FIXME: Infinite loop here ?
        self._update_serialized_content(value)

    @property
    def body(self) -> bytes | None:
        """Alias for content()

        Returns:
            bytes | None: The request body / content
        """
        return self.content

    @body.setter
    def body(self, value: bytes | str | None):
        self.content = value

    # TODO: Convert former mappings
    def update_serializer_from_content_type(
        self,
        content_type: ImplementedContentTypesTp | str | None = None,
        fail_silently: bool = False,
    ):
        # Strip the boundary param so we can use our content-type to serializer map
        _content_type: str = get_header_value_without_params(
            content_type or self.headers.get("Content-Type") or ""
        )

        serializer = None
        if _content_type in IMPLEMENTED_CONTENT_TYPES:
            serializer = CONTENT_TYPE_TO_SERIALIZER.get(_content_type)

        if serializer is None:
            if fail_silently:
                serializer = self._serializer
            else:
                raise FormNotParsedException(
                    f"Unimplemented form content-type: {_content_type}"
                )
        self._set_serializer(serializer)

    @property
    def content_type(self) -> str | None:
        """The Content-Type header value.

        Returns:
            str | None: <=> self.headers.get("Content-Type")
        """
        return self.headers.get("Content-Type")

    @content_type.setter
    def content_type(self, value: str) -> str | None:
        self.headers["Content-Type"] = value

    def create_defaultform(
        self,
        content_type: ImplementedContentTypesTp | str | None = None,
        update_header: bool = True,
    ) -> MutableMapping[Any, Any]:
        """Creates the form if it doesn't exist, else returns the existing one

        Args:
            content_type (IMPLEMENTED_CONTENT_TYPES_TP | None, optional): The form content-type. Defaults to None.
            update_header (bool, optional): Whether to update the header. Defaults to True.

        Raises:
            FormNotParsedException: Thrown when provided content-type has no implemented form-serializer
            FormNotParsedException: Thrown when the raw content could not be parsed.

        Returns:
            MutableMapping[Any, Any]: The mapped form.
        """
        if not self._is_form_initialized or content_type:
            self.update_serializer_from_content_type(content_type)

            # Set content-type if it does not exist
            if update_header or not self.headers.get_all("Content-Type"):
                self.headers["Content-Type"] = content_type

        serializer = self._serializer
        if serializer is None:
            raise FormNotParsedException(
                f"Form of content-type {self.content_type} not implemented."
            )

        # Create default form.
        if not self.content:
            self._deserialized_content = serializer.get_empty_form(self)
        elif self._deserialized_content is None:
            self._deserialize_content()

        if self._deserialized_content is None:
            raise FormNotParsedException(
                f"Could not parse content to {serializer.deserialized_type()}\nContent:{self._content}"
            )

        if not isinstance(self._deserialized_content, serializer.deserialized_type()):
            self._deserialized_content = serializer.get_empty_form(self)

        self._is_form_initialized = True
        return self._deserialized_content

    @property
    def form(self) -> MutableMapping[Any, Any]:
        """Mapping from content parsed accordingly to Content-Type

        Raises:
            FormNotParsedException: The content could not be parsed accordingly to Content-Type

        Returns:
            MutableMapping[Any, Any]: The mapped request form
        """
        if not self._is_form_initialized:
            self.update_serializer_from_content_type()

        self.create_defaultform()
        if self._deserialized_content is None:
            raise FormNotParsedException()

        self._is_form_initialized = True
        return self._deserialized_content

    @form.setter
    def form(self, form: MutableMapping[Any, Any]):
        if not self._is_form_initialized:
            self.update_serializer_from_content_type()
            self._is_form_initialized = True

        self._deserialized_content = form

        # Update raw _content
        self._serialize_content()

    # TODO: Convert from the previous form mapping
    def _set_serializer(self, serializer: FormSerializer | None):
        # Update the serializer
        old_serializer = self._serializer
        self._serializer = serializer

        if (
            old_serializer is None
            or type(serializer) == type(old_serializer)
            or serializer is None
        ):
            # We don't have any content to update.
            return

        if old_serializer is None:
            if self.content is not None:
                self._deserialized_content = serializer.deserialize(self.content, self)
            return

        old_form = self._deserialized_content

        if old_form is None:
            return

        # Convert the form to an intermediate format for easier conversion
        exported_form = old_serializer.export_form(old_form)

        # Parse the intermediate data to the new serializer format
        imported_form = serializer.import_form(exported_form, self)
        self._deserialized_content = imported_form

    def _update_serializer_and_get_form(
        self, serializer: FormSerializer
    ) -> MutableMapping[Any, Any] | None:
        # Set the serializer and update the content
        self._set_serializer(serializer)

        # Return the new form
        return self._deserialized_content

    def _update_serializer_and_set_form(
        self, serializer: FormSerializer, form: MutableMapping[Any, Any]
    ) -> None:
        # NOOP when the serializer is the same
        self._set_serializer(serializer)

        self._update_deserialized_content(form)

    @property
    def urlencoded_form(self) -> QueryParams:
        self._is_form_initialized = True
        return cast(
            QueryParams,
            self._update_serializer_and_get_form(URLEncodedFormSerializer()),
        )

    @urlencoded_form.setter
    def urlencoded_form(self, form: QueryParams):
        self._is_form_initialized = True
        self._update_serializer_and_set_form(URLEncodedFormSerializer(), form)

    @property
    def json_form(self) -> dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]:
        self._is_form_initialized = True
        if self._update_serializer_and_get_form(JSONFormSerializer()) is None:
            serializer = cast(JSONFormSerializer, self._serializer)
            self._deserialized_content = serializer.get_empty_form(self)

        return self._deserialized_content

    @json_form.setter
    def json_form(self, form: dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]):
        self._is_form_initialized = True
        self._update_serializer_and_set_form(JSONFormSerializer(), JSONForm(form))

    def _ensure_multipart_content_type(self) -> str:
        content_types_headers = self.headers.get_all("Content-Type")
        pattern = re.compile(
            r"^multipart/form-data;\s*boundary=([^;\s]+)", re.IGNORECASE
        )

        # Find a valid multipart content-type header with a valid boundary
        matched_content_type: str | None = None
        for content_type in content_types_headers:
            if pattern.match(content_type):
                matched_content_type = content_type
                break

        # If no boundary was found, overwrite the Content-Type header
        # If an user wants to avoid this behaviour,they should manually create a MultiPartForm(), convert it to bytes
        #   and pass it as raw_form()
        if matched_content_type is None:
            # TODO: Randomly generate this?
            new_content_type = (
                "multipart/form-data; boundary=----WebKitFormBoundaryy6klzjxzTk68s1dI"
            )
            self.headers["Content-Type"] = new_content_type
            return new_content_type

        return matched_content_type

    @property
    def multipart_form(self) -> MultiPartForm:
        self._is_form_initialized = True

        # Keep boundary even if content-type has changed
        if isinstance(self._deserialized_content, MultiPartForm):
            return self._deserialized_content

        # We do not have an existing form, so we have to ensure we have a content-type header with a boundary
        self._ensure_multipart_content_type()

        # Serialize the current form and try to parse it with the new serializer
        form = self._update_serializer_and_get_form(MultiPartFormSerializer())
        serializer = cast(MultiPartFormSerializer, self._serializer)

        # Set a default value
        if not form:
            self._deserialized_content = serializer.get_empty_form(self)

        # get_empty_form() fails when the request doesn't have a valid Content-Type multipart/form-data with a boundary
        if self._deserialized_content is None:
            raise FormNotParsedException(
                f"Could not parse content to {serializer.deserialized_type()}"
            )

        return self._deserialized_content

    @multipart_form.setter
    def multipart_form(self, form: MultiPartForm):
        self._is_form_initialized = True
        if not isinstance(self._deserialized_content, MultiPartForm):
            # Generate a multipart header because we don't have any boundary to format the multipart.
            self._ensure_multipart_content_type()

        return self._update_serializer_and_set_form(
            MultiPartFormSerializer(), cast(MutableMapping, form)
        )

    @property
    def cookies(self) -> multidict.MultiDictView[str, str]:
        """
        The request cookies.
        For the most part, this behaves like a dictionary.
        Modifications to the MultiDictView update `Request.headers`, and vice versa.
        """
        return multidict.MultiDictView(self._get_cookies, self._set_cookies)

    def _get_cookies(self) -> tuple[tuple[str, str], ...]:
        h = self.headers.get_all("Cookie")
        return tuple(cookies.parse_cookie_headers(h))

    def _set_cookies(self, value: tuple[tuple[str, str], ...]):
        self.headers["cookie"] = cookies.format_cookie_header(value)

    # TODO: Allow passing a dict/Mapping
    @cookies.setter
    def cookies(self, value: tuple[tuple[str, str], ...]):
        self._set_cookies(value)

    @property
    def host_header(self) -> str | None:
        return self.headers["Host"]

    @host_header.setter
    def host_header(self, value: str | None):
        self.headers["Host"] = value

    def text(self, encoding="utf-8") -> str:
        return self.content.decode(encoding)
