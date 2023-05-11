from __future__ import annotations

import urllib.parse
import unittest
from typing import Sequence, Protocol, Any, Iterator
from abc import ABC, abstractmethod
from requests.structures import CaseInsensitiveDict


from mitmproxy.coretypes import multidict

from .headers import Headers
from pyscalpel.encoding import always_bytes, always_str
import json

from typing import cast, Mapping, Protocol, Mapping, Any
from requests_toolbelt.multipart.decoder import BodyPart, MultipartDecoder
from requests_toolbelt.multipart.encoder import MultipartEncoder, encode_with
from email.message import Message
from email.parser import Parser
from email.header import decode_header, make_header
from email.headerregistry import ContentDispositionHeader
from urllib.parse import quote as urllibquote
from pyscalpel.http.mime import parse_mime_header_value
from pyscalpel.http.mime import (
    unparse_header_value,
    parse_header,
    extract_boundary,
    find_header_param,
    update_header_param,
)

# Define constants to avoid typos.
CONTENT_TYPE_KEY = "Content-Type"
CONTENT_DISPOSITION_KEY = "Content-Disposition"

# TODO: Unit tests

# Multipart needs the Content-Type header for the boundary parameter
# So Serializer needs an object that references the header
# This is also used as a Forward
class ObjectWithHeaders(Protocol):
    headers: Headers


# Abstract base class
class Serializer(ABC):
    @abstractmethod
    def serialize(self, deserialized_body: Any, req: ObjectWithHeaders) -> bytes:
        ...

    @abstractmethod
    def deserialize(self, body: bytes, req: ObjectWithHeaders) -> Any:
        ...


class QueryParamsView(multidict.MultiDictView[str, str]):
    def __init__(self, origin: multidict.MultiDictView[str, str]) -> None:
        super().__init__(origin._getter, origin._setter)

    def __setitem__(self, key: int | str | bytes, value: int | str | bytes) -> None:
        super().__setitem__(always_str(key), always_str(value))


class QueryParams(multidict.MultiDict[bytes, bytes]):
    def __init__(self, fields: Sequence[tuple[bytes, bytes]]) -> None:
        super().__init__(fields)

    def __setitem__(self, key: int | str | bytes, value: int | str | bytes) -> None:
        super().__setitem__(always_bytes(key), always_bytes(value))


class URLEncodedFormSerializer(Serializer):
    def serialize(
        self, deserialized_body: multidict.MultiDict[bytes, bytes], req=...
    ) -> bytes:
        return b"&".join(b"=".join(field) for field in deserialized_body.items())

    def deserialize(self, body: bytes, req=...) -> QueryParams:
        fields = urllib.parse.parse_qsl(body) if isinstance(body, bytes) else tuple()
        return QueryParams(fields)


JSON_KEY_TYPES = str | int | float
JSON_VALUE_TYPES = (
    str
    | int
    | float
    | bool
    | None
    | list["JSON_VALUE_TYPES"]
    | dict[JSON_KEY_TYPES, "JSON_VALUE_TYPES"]
)


class JSONFormSerializer(Serializer):
    def serialize(
        self, deserialized_body: Mapping[JSON_KEY_TYPES, JSON_VALUE_TYPES], req=...
    ) -> bytes:
        return json.dumps(deserialized_body).encode("utf-8")

    def deserialize(
        self, body: bytes, req=...
    ) -> dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]:
        return json.loads(body) if body else dict()


class OctetStreamSerializer(Serializer):
    """Does nothing"""

    def serialize(self, deserialized_body: bytes, req=...) -> bytes:
        return deserialized_body

    def deserialize(self, body: bytes, req=...) -> bytes:
        return body


class MultiPartFormField:
    headers: CaseInsensitiveDict[str]
    content: bytes
    encoding: str

    def __init__(self, body_part: BodyPart):
        self.headers = MultiPartFormField._fix_headers(
            cast(Mapping[bytes, bytes], body_part.headers)
        )
        self.content = body_part.content

    @classmethod
    def make(
        cls,
        name: str,
        body: bytes = b"",
        content_type: str = "application/octet-stream",
        encoding: str = "utf-8",
    ) -> MultiPartFormField:
        urlencoded_name: str = urllibquote(name)
        urlencoded_content_type = urllibquote(content_type)
        content = f'{CONTENT_DISPOSITION_KEY}: form-data; name="{urlencoded_name}"'
        content += f"\r\n{CONTENT_TYPE_KEY}: {urlencoded_content_type}\r\n\r\n"
        encoded_content: bytes = content.encode(encoding) + body
        body_part = BodyPart(encoded_content, encoding)
        return cls(body_part)

    @staticmethod
    def __serialize_content(
        content: bytes, headers: Mapping[str | bytes, str | bytes]
    ) -> bytes:
        # Prepend content with headers
        merged_content: bytes = b""
        header_lines = (
            always_bytes(key) + b": " + always_bytes(value)
            for key, value in headers.items()
        )
        merged_content += b"\r\n".join(header_lines)
        merged_content += b"\r\n\r\n"
        merged_content += content
        return merged_content

    def __bytes__(self) -> bytes:
        return self.__serialize_content(
            self.content,
            cast(Mapping[bytes | str, bytes | str], self.headers),
        )

    @staticmethod
    def _fix_headers(headers: Mapping[bytes, bytes]) -> CaseInsensitiveDict[str]:
        # Fix the headers key by converting them to strings
        # https://github.com/requests/toolbelt/pull/353

        fixed_headers: CaseInsensitiveDict[str] = CaseInsensitiveDict()
        for key, value in headers.items():
            fixed_headers[always_str(key)] = always_str(value.decode())
        return fixed_headers

    @staticmethod
    def _unfix_headers(headers: Mapping[str, str]) -> CaseInsensitiveDict[bytes]:
        # Unfix the headers key by converting them to bytes

        unfixed_headers: CaseInsensitiveDict[bytes] = CaseInsensitiveDict()
        for key, value in headers.items():
            unfixed_headers[always_bytes(key)] = always_bytes(value)  # type: ignore requests_toolbelt uses wrong types but it still works fine.
        return unfixed_headers

    @property
    def text(self) -> str:
        return self.content.decode(self.encoding)

    @property
    def content_type(self) -> str | None:
        return self.headers.get(CONTENT_TYPE_KEY)

    @content_type.setter
    def content_type(self, content_type: str | None) -> None:
        headers = self.headers
        if content_type is None:
            del headers[CONTENT_TYPE_KEY]
        else:
            headers[CONTENT_TYPE_KEY] = content_type

    def _parse_disposition(self) -> list[tuple[str, str]]:
        header_key = CONTENT_DISPOSITION_KEY
        header_value = self.headers[header_key]
        return parse_header(header_key, header_value)

    def _unparse_disposition(self, parsed_header: list[tuple[str, str]]):
        unparsed = unparse_header_value(parsed_header)
        self.headers[CONTENT_DISPOSITION_KEY] = unparsed

    def get_disposition_param(self, key: str) -> tuple[str, str | None] | None:
        parsed_disposition = self._parse_disposition()
        try:
            return find_header_param(parsed_disposition, key)
        except StopIteration as exc:
            raise StopIteration(
                "Content-Disposition header was not found in multipart form or could not be parsed."
            ) from exc

    def set_disposition_param(self, key: str, value: str):
        parsed = self._parse_disposition()
        updated = update_header_param(parsed, key, value)
        self._unparse_disposition(cast(list[tuple[str, str]], updated))

    @property
    def name(self) -> str:
        # Assume name is always present
        return cast(tuple[str, str], self.get_disposition_param("name"))[1]

    @name.setter
    def name(self, value: str):
        self.set_disposition_param("name", value)

    @property
    def filename(self) -> str | None:
        param = self.get_disposition_param("filename")
        return param and param[1]

    @filename.setter
    def filename(self, value: str):
        self.set_disposition_param("filename", value)


class MultiPartForm(Mapping[str, MultiPartFormField]):
    fields: list[MultiPartFormField]
    content_type: str
    encoding: str

    def __init__(
        self,
        fields: Sequence[MultiPartFormField],
        content_type: str,
        encoding: str = "utf-8",
    ):
        self.content_type = content_type
        self.encoding = encoding
        super().__init__()
        self.fields = list(fields)

    @classmethod
    def from_bytes(
        cls, content: bytes, content_type: str, encoding: str = "utf-8"
    ) -> MultiPartForm:
        decoder = MultipartDecoder(content, content_type, encoding=encoding)
        parts: tuple[BodyPart] = decoder.parts
        fields: tuple[MultiPartFormField, ...] = tuple(
            MultiPartFormField(body_part) for body_part in parts
        )
        return cls(fields, content_type, encoding)

    @property
    def boundary(self) -> bytes:
        return extract_boundary(self.content_type, self.encoding)

    def __bytes__(self) -> bytes:
        boundary = self.boundary
        serialized = b""
        encoding = self.encoding
        for field in self.fields:
            serialized += boundary + b"\r\n"

            for key, val in field.headers.items():
                serialized += (
                    key.encode(encoding) + b": " + val.encode(encoding) + b"\r\n"
                )

            serialized += b"\r\n" + field.content + b"\r\n"

        serialized += boundary + b"\r\n\r\n"
        return serialized

    # Override
    def get_all(self, key: str) -> list[MultiPartFormField]:
        """
        Return the list of all values for a given key.
        If that key is not in the MultiDict, the return value will be an empty list.
        """
        return [field for field in self.fields if key == field.name]

    def setdefault(
        self, key: str, default: MultiPartFormField | None = None
    ) -> MultiPartFormField:
        found = self.get(key)
        if found is None:
            default = default or MultiPartFormField.make(key)
            self[key] = default
            return default

        return found

    def __setitem__(self, key: str, value: MultiPartFormField | bytes) -> None:
        if isinstance(value, bytes):
            field = MultiPartFormField.make(key)
            field.content = value
            value = field

        for i, field in enumerate(self.fields):
            if field.name == key:
                self.fields[i] = value
                return

        self.add(value)

    def __getitem__(self, key: str) -> MultiPartFormField:
        values = self.get_all(key)
        if not values:
            raise KeyError(key)
        return values[0]

    def __len__(self) -> int:
        return len(self.fields)

    def __eq__(self, other) -> bool:
        if isinstance(other, MultiPartForm):
            return self.fields == other.fields
        return False

    def __iter__(self) -> Iterator[MultiPartFormField]:
        seen = set()
        for field in self.fields:
            if field not in seen:
                seen.add(field)
                yield field

    def insert(self, index: int, value: MultiPartFormField) -> None:
        """
        Insert an additional value for the given key at the specified position.
        """
        self.fields = self.fields[:index] + [value] + self.fields[index:]

    def add(self, value: MultiPartFormField) -> None:
        self.insert(len(self.fields), value)

    def __repr__(self):
        fields = (repr(field) for field in self.fields)
        return f"{type(self).__name__}[{', '.join(fields)}]"


class MultiPartFormSerializer(Serializer):
    def serialize(
        self, deserialized_body: MultiPartForm, req: ObjectWithHeaders
    ) -> bytes:
        content_type: str | None = req.headers.get(CONTENT_TYPE_KEY)

        if content_type:
            deserialized_body.content_type = content_type

        return bytes(deserialized_body)

    def deserialize(self, body: bytes, req: ObjectWithHeaders) -> MultiPartForm:
        content_type: str | None = req.headers.get(CONTENT_TYPE_KEY)

        assert content_type

        if not body:
            return MultiPartForm(tuple(), content_type)

        return MultiPartForm.from_bytes(body, content_type)


CONTENT_TYPE_TO_SERIALIZER: CaseInsensitiveDict[Serializer] = CaseInsensitiveDict(
    {
        "application/x-www-form-urlencoded": URLEncodedFormSerializer(),
        "application/json": JSONFormSerializer(),
        "application/octet-stream": OctetStreamSerializer(),
        "multipart/form-data": MultiPartFormSerializer(),
    }
)
