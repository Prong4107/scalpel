from __future__ import annotations

import os
import urllib.parse
from typing import Sequence, Protocol, Any, Iterator, TypeVar
from abc import ABC, abstractmethod
from requests.structures import CaseInsensitiveDict
from io import TextIOWrapper, BufferedReader, IOBase
import mimetypes
from mitmproxy.coretypes import multidict
from abc import ABCMeta

from pyscalpel.http.headers import Headers
from pyscalpel.encoding import always_bytes, always_str
import json

from typing import cast, MutableMapping, Protocol, Mapping, Any, Literal
from requests_toolbelt.multipart.decoder import (
    BodyPart,
    MultipartDecoder,
    ImproperBodyPartContentException,
)
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
class FormSerializer(ABC):
    @abstractmethod
    def serialize(self, deserialized_body: Any, req: ObjectWithHeaders) -> bytes:
        ...

    @abstractmethod
    def deserialize(self, body: bytes, req: ObjectWithHeaders) -> Any:
        ...

    @abstractmethod
    def get_empty_form(self, req: ObjectWithHeaders) -> Any:
        ...

    @abstractmethod
    def deserialized_type(self) -> type:
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


class URLEncodedFormSerializer(FormSerializer):
    def serialize(
        self, deserialized_body: multidict.MultiDict[bytes, bytes], req=...
    ) -> bytes:
        return b"&".join(b"=".join(field) for field in deserialized_body.items())

    def deserialize(self, body: bytes, req=...) -> QueryParams:
        fields = urllib.parse.parse_qsl(body) if isinstance(body, bytes) else tuple()
        return QueryParams(fields)

    def get_empty_form(self, req=...) -> Any:
        return QueryParams(tuple())

    def deserialized_type(self) -> type[QueryParams]:
        return QueryParams


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


class JSONForm(dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]):
    pass


class JSONFormSerializer(FormSerializer):
    def serialize(
        self, deserialized_body: Mapping[JSON_KEY_TYPES, JSON_VALUE_TYPES], req=...
    ) -> bytes:
        return json.dumps(deserialized_body).encode("utf-8")

    def deserialize(self, body: bytes, req=...) -> JSONForm:
        return JSONForm(json.loads(body)) if body else JSONForm()

    def get_empty_form(self, req=...) -> JSONForm:
        return JSONForm()

    def deserialized_type(self) -> type[JSONForm]:
        return JSONForm


class OctetStreamSerializer(FormSerializer):
    """Does nothing"""

    def serialize(self, deserialized_body: bytes, req=...) -> bytes:
        return deserialized_body

    def deserialize(self, body: bytes, req=...) -> bytes:
        return body

    def get_empty_form(self, req=...) -> bytes:
        return b""

    def deserialized_type(self) -> type[bytes]:
        return bytes


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
        urlencoded_name: str = urllibquote(name)
        urlencoded_content_type = urllibquote(content_type)

        disposition = f'form-data; name="{urlencoded_name}"'
        if filename is not None:
            disposition += f'; filename="{urllibquote(filename)}"'

        headers = CaseInsensitiveDict(
            {
                CONTENT_DISPOSITION_KEY: disposition,
                CONTENT_TYPE_KEY: urlencoded_content_type,
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


if __name__ == "__main__":
    import unittest
    import tempfile
    from io import BytesIO

    class MultiPartFormTestCase(unittest.TestCase):
        def setUp(self):
            headers = CaseInsensitiveDict(
                {
                    "Content-Disposition": 'form-data; name="file"; filename="example.txt"',
                    "Content-Type": "text/plain",
                }
            )
            content = b"This is the content of the file."
            encoding = "utf-8"
            self.form_field = MultiPartFormField(headers, content, encoding)
            self.form = MultiPartForm([self.form_field], "multipart/form-data")

        def test_mapping_interface(self):
            self.assertIsInstance(self.form, Mapping)

        def test_get_all(self):
            key = "file"
            expected_values = [self.form_field]
            values = self.form.get_all(key)
            self.assertEqual(values, expected_values)

        def test_get_all_empty_key(self):
            key = "nonexistent"
            expected_values = []
            values = self.form.get_all(key)
            self.assertEqual(values, expected_values)

        def test_get(self):
            key = "file"
            expected_value = self.form_field
            value = self.form.get(key)
            self.assertEqual(value, expected_value)

        def test_get_default(self):
            key = "nonexistent"
            default = MultiPartFormField.make("kjdsqkjdhdsqsq")
            expected_value = default
            value = self.form.get(key, default)
            self.assertEqual(value, expected_value)

        def test_del_all(self):
            key = "file"
            self.form.del_all(key)
            values = self.form.get_all(key)
            self.assertEqual(values, [])

        def test_del_all_empty_key(self):
            key = "nonexistent"
            self.form.del_all(key)  # No exception should be raised

        def test_delitem(self):
            key = "file"
            del self.form[key]
            values = self.form.get_all(key)
            self.assertEqual(values, [])

        # def test_delitem_key_error(self):
        #     key = "nonexistent"
        #     with self.assertRaises(KeyError):
        #         del self.form[key]

        def test_set(self):
            key = "new_file"
            value = MultiPartFormField.make(key)
            self.form[key] = value
            self.assertEqual(self.form.get(key), value)

        def test_set_bytes_value(self):
            key = "new_file"
            value = b"example content"
            self.form[key] = value
            form_field = cast(MultiPartFormField, self.form.get(key))
            self.assertEqual(form_field.name, key)
            self.assertEqual(form_field.content, value)

        def test_set_io_value(self):
            key = "new_file"
            value = BytesIO(b"example content")
            self.form[key] = value
            form_field = cast(MultiPartFormField, self.form.get(key))
            self.assertIsInstance(form_field, MultiPartFormField)
            self.assertEqual(form_field.name, key)
            self.assertEqual(form_field.content, b"example content")

        def test_set_none_value(self):
            key = "file"
            self.form[key] = None
            values = self.form.get_all(key)
            self.assertEqual(values, [])

        def test_set_default(self):
            key = "nonexistent"
            default = MultiPartFormField.make(key)
            value = self.form.setdefault(key, default)
            self.assertEqual(value, default)
            self.assertEqual(self.form.get(key), default)

        def test_set_default_existing(self):
            key = "file"
            default = MultiPartFormField.make(key)
            value = self.form.setdefault(key, default)
            self.assertEqual(value, self.form_field)
            self.assertEqual(self.form.get(key), self.form_field)

        def test_len(self):
            expected_length = 1
            length = len(self.form)
            self.assertEqual(length, expected_length)

        def test_iter(self):
            expected_fields = [self.form_field]
            fields = list(self.form)
            self.assertEqual(fields, expected_fields)

        def test_eq_same_fields(self):
            form2 = MultiPartForm([self.form_field], "multipart/form-data")
            self.assertEqual(self.form, form2)

        def test_eq_different_fields(self):
            form2 = MultiPartForm([], "multipart/form-data")
            self.assertNotEqual(self.form, form2)

        def test_items(self):
            expected_items = [("file", self.form_field)]
            items = list(self.form.items())
            self.assertEqual(items, expected_items)

        def test_values(self):
            expected_values = [self.form_field]
            values = list(self.form.values())
            self.assertEqual(values, expected_values)

        def test_keys(self):
            expected_keys = ["file"]
            keys = list(self.form.keys())
            self.assertEqual(keys, expected_keys)

        # def test_repr(self):
        #     expected_repr = "MultiPartForm[<MultiPartFormField(headers={'Content-Disposition': 'form-data; name=\"file\"; filename=\"example.txt\"', 'Content-Type': 'text/plain'}, content=b'This is the content of the file.', encoding='utf-8')>]"
        #     form_repr = repr(self.form)
        #     self.assertEqual(form_repr, expected_repr)

    class URLEncodedFormSerializerTestCase(unittest.TestCase):
        def test_serialize(self):
            serializer = URLEncodedFormSerializer()
            deserialized_body = multidict.MultiDict(
                ((b"name", b"John"), (b"age", b"30"))
            )
            expected = b"name=John&age=30"
            result = serializer.serialize(deserialized_body)
            self.assertEqual(result, expected)

        def test_deserialize(self):
            serializer = URLEncodedFormSerializer()
            body = b"name=John&age=30"
            expected = QueryParams([(b"name", b"John"), (b"age", b"30")])
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_deserialize_empty_body(self):
            serializer = URLEncodedFormSerializer()
            body = b""
            expected = QueryParams([])
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_get_empty_form(self):
            serializer = URLEncodedFormSerializer()
            expected = QueryParams([])
            result = serializer.get_empty_form()
            self.assertEqual(result, expected)

        def test_deserialized_type(self):
            serializer = URLEncodedFormSerializer()
            expected = QueryParams
            result = serializer.deserialized_type()
            self.assertEqual(result, expected)

    class JSONFormSerializerTestCase(unittest.TestCase):
        def test_serialize(self):
            serializer = JSONFormSerializer()
            deserialized_body: dict[JSON_KEY_TYPES, JSON_VALUE_TYPES] = {
                "name": "John",
                "age": 30,
            }
            expected = b'{"name": "John", "age": 30}'
            result = serializer.serialize(deserialized_body)
            self.assertEqual(result, expected)

        def test_deserialize(self):
            serializer = JSONFormSerializer()
            body = b'{"name": "John", "age": 30}'
            expected = {"name": "John", "age": 30}
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_deserialize_empty_body(self):
            serializer = JSONFormSerializer()
            body = b""
            expected = None
            result = serializer.deserialize(body)
            self.assertEqual(result, expected)

        def test_get_empty_form(self):
            serializer = JSONFormSerializer()
            expected = JSONForm({})
            result = serializer.get_empty_form()
            self.assertEqual(result, expected)

        def test_deserialized_type(self):
            serializer = JSONFormSerializer()
            expected = JSONForm
            result = serializer.deserialized_type()
            self.assertEqual(result, expected)

    class MultiPartFormFieldTestCase(unittest.TestCase):
        def test_init(self):
            headers = CaseInsensitiveDict(
                {
                    "Content-Disposition": 'form-data; name="file"; filename="example.txt"',
                    "Content-Type": "text/plain",
                }
            )
            content = b"This is the content of the file."
            encoding = "utf-8"
            expected_headers = CaseInsensitiveDict(
                {
                    "Content-Disposition": 'form-data; name="file"; filename="example.txt"',
                    "Content-Type": "text/plain",
                }
            )
            expected_content = b"This is the content of the file."
            expected_encoding = "utf-8"
            result = MultiPartFormField(headers, content, encoding)
            self.assertEqual(result.headers, expected_headers)
            self.assertEqual(result.content, expected_content)
            self.assertEqual(result.encoding, expected_encoding)

        def test_file_upload(self):
            with tempfile.NamedTemporaryFile(delete=False) as temp_file:
                temp_file.write(b"This is the content of the file.")

            filename = temp_file.name
            content_type = "text/plain"

            form_field = MultiPartFormField.from_file(
                "file", filename, content_type=content_type
            )

            self.assertEqual(form_field.name, "file")
            self.assertEqual(form_field.filename, os.path.basename(filename))
            self.assertEqual(form_field.content_type, content_type)
            self.assertEqual(form_field.content, b"This is the content of the file.")

            os.remove(filename)

    unittest.main()
