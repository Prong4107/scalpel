from __future__ import annotations

import os
from typing import Literal, Sequence, Any, Iterator
from requests.structures import CaseInsensitiveDict
from io import TextIOWrapper, BufferedReader, IOBase
import mimetypes

from collections.abc import Mapping, MutableMapping

from pyscalpel.encoding import always_bytes, always_str

from typing import cast, Any, Iterable
from requests_toolbelt.multipart.decoder import (
    BodyPart,
    MultipartDecoder,
    ImproperBodyPartContentException,
)
from urllib.parse import quote as urllibquote
from pyscalpel.http.mime import (
    unparse_header_value,
    parse_header,
    extract_boundary,
    find_header_param,
    update_header_param,
)
from pyscalpel.http.body.abstract import (
    ExportedForm,
    Form,
    TupleExportedForm,
)

from .abstract import FormSerializer, ObjectWithHeaders, Scalars

# Define constants to avoid typos.
CONTENT_TYPE_KEY = "Content-Type"
CONTENT_DISPOSITION_KEY = "Content-Disposition"


# def


class MultiPartFormField:
    headers: CaseInsensitiveDict[str]
    content: bytes
    encoding: str

    def __init__(
        self,
        headers: CaseInsensitiveDict[str],
        content: bytes = b"",
        encoding: str = "utf-8",
    ):
        self.headers = headers
        self.content = content
        self.encoding = encoding

    @classmethod
    def from_body_part(cls, body_part: BodyPart):
        headers = cls._fix_headers(cast(Mapping[bytes, bytes], body_part.headers))
        return cls(headers, body_part.content, body_part.encoding)

    @classmethod
    def make(
        cls,
        name: str,
        filename: str | None = None,
        body: bytes = b"",
        content_type: str = "application/octet-stream",
        encoding: str = "utf-8",
    ) -> MultiPartFormField:
        urlencoded_name: str = urllibquote(name, safe="[]{}/.;,?!§:@#~^$*")
        urlencoded_content_type = urllibquote(content_type)

        disposition = f'form-data; name="{urlencoded_name}"'
        if filename is not None:
            # When the param is a file, add a filename MIME param and a content-type header
            disposition += f'; filename="{urllibquote(filename)}"'
            headers = CaseInsensitiveDict(
                {
                    CONTENT_DISPOSITION_KEY: disposition,
                    CONTENT_TYPE_KEY: urlencoded_content_type,
                }
            )
        else:
            headers = CaseInsensitiveDict(
                {
                    CONTENT_DISPOSITION_KEY: disposition,
                }
            )

        return cls(headers, body, encoding)

    @staticmethod
    def from_file(
        name: str,
        file: TextIOWrapper | BufferedReader | str | IOBase,
        filename: str | None = None,
        content_type: str | None = None,
        encoding: str | None = None,
    ):
        if isinstance(file, str):
            file = open(file, mode="rb")

        if filename is None:
            match file:
                case TextIOWrapper() | BufferedReader():
                    filename = file.name
                case _:
                    filename = name

        # Guess the MIME content-type from the file extension
        if content_type is None:
            content_type = (
                mimetypes.guess_type(filename)[0] or "application/octet-stream"
            )

        # Read the whole file into memory
        content: bytes
        match file:
            case TextIOWrapper():
                content = file.read(-1).encode(file.encoding)
                # Override file.encoding if provided.
                encoding = encoding or file.encoding
            case BufferedReader() | IOBase():
                content = file.read(-1)

        instance = MultiPartFormField.make(
            name,
            filename=os.path.basename(filename),
            body=content,
            content_type=content_type,
            encoding=encoding or "utf-8",
        )

        file.close()

        return instance

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
            MultiPartFormField.from_body_part(body_part) for body_part in parts
        )
        return cls(fields, content_type, encoding)

    @property
    def boundary(self) -> bytes:
        return extract_boundary(self.content_type, self.encoding)

    # TODO: Unit test this
    def __bytes__(self) -> bytes:
        boundary = self.boundary
        serialized = b""
        encoding = self.encoding
        for field in self.fields:
            serialized += b"--" + boundary + b"\r\n"

            for key, val in field.headers.items():
                serialized += (
                    key.encode(encoding) + b": " + val.encode(encoding) + b"\r\n"
                )
            serialized += b"\r\n" + field.content + b"\r\n"

        serialized += b"--" + boundary + b"--\r\n\r\n"
        return serialized

    # Override
    def get_all(self, key: str) -> list[MultiPartFormField]:
        """
        Return the list of all values for a given key.
        If that key is not in the MultiDict, the return value will be an empty list.
        """
        return [field for field in self.fields if key == field.name]

    def get(
        self, key: str, default: MultiPartFormField | None = None
    ) -> MultiPartFormField | None:
        values = self.get_all(key)
        if not values:
            return default

        return values[0]

    def del_all(self, key: str):
        # Mutate object to avoid invalidating user references to fields
        for field in self.fields:
            if key == field.name:
                self.fields.remove(field)

    def __delitem__(self, key: str):
        self.del_all(key)

    def set(
        self,
        key: str,
        value: TextIOWrapper
        | BufferedReader
        | IOBase
        | MultiPartFormField
        | bytes
        | None,
    ) -> None:
        new_field: MultiPartFormField
        match value:
            case MultiPartFormField():
                new_field = value
            case bytes():
                new_field = MultiPartFormField.make(key)
                new_field.content = value
            case IOBase():
                new_field = MultiPartFormField.from_file(key, value)
            case None:
                self.del_all(key)
                return
            case _:
                raise RuntimeError("Wrong type was passed to MultiPartForm.set")

        for i, field in enumerate(self.fields):
            if field.name == key:
                self.fields[i] = new_field
                return

        self.add(new_field)

    def setdefault(
        self, key: str, default: MultiPartFormField | None = None
    ) -> MultiPartFormField:
        found = self.get(key)
        if found is None:
            default = default or MultiPartFormField.make(key)
            self[key] = default
            return default

        return found

    def __setitem__(
        self,
        key: str,
        value: TextIOWrapper
        | BufferedReader
        | MultiPartFormField
        | IOBase
        | bytes
        | None,
    ) -> None:
        self.set(key, value)

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

    def items(self) -> tuple[tuple[str, MultiPartFormField], ...]:
        fields = self.fields
        items = ((i.name, i) for i in fields)
        return tuple(items)

    def keys(self) -> tuple[str, ...]:
        return tuple(field.name for field in self.fields)

    def values(self) -> tuple[MultiPartFormField, ...]:
        return tuple(self.fields)


def scalar_to_bytes(scalar: Scalars | None) -> bytes:
    match scalar:
        case str() | bytes():
            return always_bytes(scalar)
        case int() | float():
            return always_bytes(str(scalar))
        case bool():
            return b"1" if scalar else b"0"
        case _:
            return b""


def scalar_to_str(scalar: Scalars | None) -> str:
    match scalar:
        case str() | bytes():
            return always_str(scalar)
        case int() | float():
            return str(scalar)
        case bool():
            return "1" if scalar else "0"
        case _:
            return ""


class MultiPartFormSerializer(FormSerializer):
    def serialize(
        self, deserialized_body: MultiPartForm, req: ObjectWithHeaders
    ) -> bytes:
        content_type: str | None = req.headers.get(CONTENT_TYPE_KEY)

        if content_type:
            deserialized_body.content_type = content_type

        return bytes(deserialized_body)

    def deserialize(self, body: bytes, req: ObjectWithHeaders) -> MultiPartForm | None:
        content_type: str | None = req.headers.get(CONTENT_TYPE_KEY)

        assert content_type

        if not body:
            return None

        try:
            return MultiPartForm.from_bytes(body, content_type)
        except ImproperBodyPartContentException:
            return None

    def get_empty_form(self, req: ObjectWithHeaders) -> Any:
        content_type: str | None = req.headers.get(CONTENT_TYPE_KEY)

        assert content_type

        return MultiPartForm(tuple(), content_type)

    def deserialized_type(self) -> type[MultiPartForm]:
        return MultiPartForm

    def import_form(
        self, exported: ExportedForm, req: ObjectWithHeaders
    ) -> MultiPartForm:
        content_type = req.headers.get("Content-Type")
        assert content_type

        sequence: Iterable[tuple[Scalars, Scalars | None]]
        match exported:
            case dict():
                sequence = exported.items()
            case tuple():
                sequence = exported

        fields = tuple(
            MultiPartFormField.make(scalar_to_str(name), body=scalar_to_bytes(body))
            for name, body in sequence
        )
        return MultiPartForm(fields, content_type)

    def export_form(self, source: Form) -> tuple[tuple[str, bytes]]:
        # Only retain name and content
        return tuple((key, field.content) for key, field in source.items())
