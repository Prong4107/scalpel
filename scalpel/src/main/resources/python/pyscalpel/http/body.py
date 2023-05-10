import urllib.parse

from typing import Sequence, Protocol, Any
from abc import ABC, abstractmethod
from requests.structures import CaseInsensitiveDict


from mitmproxy.coretypes import multidict

from .headers import Headers
from pyscalpel.encoding import always_bytes, always_str
import json

from typing import cast, Mapping, Protocol, Mapping, Any
from requests_toolbelt.multipart.decoder import BodyPart
from email.message import Message
from email.parser import Parser
from email.header import decode_header, make_header

class ObjectWithHeaders(Protocol):
    headers: Headers


class Serializer(ABC):
    @abstractmethod
    def serialize(self, deserialized_body: Any, req: ObjectWithHeaders = ...) -> bytes:
        ...

    @abstractmethod
    def deserialize(self, body: bytes | None, req: ObjectWithHeaders = ...) -> Any:
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

    def deserialize(self, body: bytes | None, req=...) -> QueryParams:
        fields = urllib.parse.parse_qsl(body) if isinstance(body, bytes) else tuple()
        return QueryParams(fields)  # ty


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
        self, body: bytes | None, req=...
    ) -> dict[JSON_KEY_TYPES, JSON_VALUE_TYPES]:
        return json.loads(body) if body else dict()


class OctetStreamSerializer(Serializer):
    """Does nothing"""

    def serialize(self, deserialized_body: bytes, req=...) -> bytes:
        return deserialized_body

    def deserialize(self, body: bytes | None, req=...) -> bytes | None:
        return body


class MultiPartFormField:
    body_part: BodyPart

    @staticmethod
    def __serialize_content(
        content: bytes, headers: Mapping[str | bytes, str | bytes]
    ) -> bytes:
        # Prepend content with headers
        merged_content: bytes = b""
        header_lines = (always_bytes(key) + b": " + always_bytes(value) for key, value in headers.items())
        merged_content += b"\r\n".join(header_lines)
        merged_content += b"\r\n\r\n"
        merged_content += content
        return merged_content

    def __init__(self, body_part: BodyPart):
        self.body_part = body_part

    def __bytes__(self) -> bytes:
        return self.__serialize_content(
            self.body_part.content,
            cast(Mapping[bytes | str, bytes | str], self.body_part.headers),
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
    def headers(self) -> CaseInsensitiveDict[str]:
        return self._fix_headers(cast(Mapping[bytes, bytes], self.body_part.headers))

    @headers.setter
    def headers(self, headers: Mapping[str, str]) -> None:
        self.body_part.headers = self._unfix_headers(headers)

    @property
    def encoding(self) -> str:
        return self.body_part.encoding

    @encoding.setter
    def encoding(self, encoding: str) -> None:
        self.body_part.encoding = encoding

    @property
    def text(self) -> str:
        return self.body_part.text

    @property
    def content_type(self) -> str | None:
        headers = self.headers
        if "Content-Type" in headers:
            return headers["Content-Type"]
        return None

    @content_type.setter
    def content_type(self, content_type: str | None) -> None:
        headers = self.headers
        if content_type is None:
            del headers["Content-Type"]
        else:
            headers["Content-Type"] = content_type
    
    def _parse_disposition(self) -> list[tuple[str,str | None]]:
        header = self.headers["Content-Disposition"]
        return decode_header(header)
        
    def _unparse_disposition(self, parsed_header: list[tuple[str, str |None]]):
        msg = Message()
        header_key, header_value = cast(tuple[str,str], parsed_header.pop(0))
        msg.add_header(header_key, header_value, *parsed_header)
        new_header:str = cast(str, msg.get(header_key))
        self.headers[header_key] = new_header
    
    @staticmethod
    def _find_param(params: Sequence[tuple[str,str | None]], key:str) -> tuple[str,str | None] | None:
        return next(param for param in params if param[0] == key)
    
    @staticmethod
    def _with_param(params: Sequence[tuple[str, str |None]], key: str, value: str | None) -> list[tuple[str, str | None]]:
        """ WIP: Copy the provided params and update or add the matching value"""
        new_params: list[tuple[str,str|None]] = list()
        found:bool = False
        for param in params:
            if key == param[0]:
                new_params.append((key,value))
                found = True
            else:
                new_params.append(param)

        if not found:
            new_params.append((key,value))
        
        return new_params
                
        
    def get_disposition_param(self, key: str) -> tuple[str, str|None] | None:
        return MultiPartFormField._find_param(self._parse_disposition(),key)
    
    def set_disposition_param(self, key:str, value:str):
        parsed = self._parse_disposition()
        updated = MultiPartFormField._with_param(parsed, key,value)
        self._unparse_disposition(updated)
        
    
    @property
    def name(self) -> str:
        # Assume name is always present
        return cast(tuple[str,str], self.get_disposition_param("name"))[0]

    @name.setter
    def name(self, value:str):
        self.set_disposition_param("name", value)
    
    @property
    def filename(self) -> str | None:
        param = self.get_disposition_param("filename")
        return param and param[1]
    
    @filename.setter
    def filename(self, value:str):
        self.set_disposition_param("filename", value)


class MultiPartForm(multidict.MultiDict[str, MultiPartFormField]):
    fields: tuple[MultiPartFormField,...]
    
    def get_all(self, key: str) -> list[MultiPartFormField]:
        """
        Return the list of all values for a given key.
        If that key is not in the MultiDict, the return value will be an empty list.
        """
        return [field for field in self.fields if key == field.name]
    

class MultiPartFormSerializer(Serializer):
    def serialize(self, deserialized_body: ..., req=...) -> ...:
        ...

    def deserialize(self, body: bytes | None, req=...) -> :
        ...


CONTENT_TYPE_TO_SERIALIZER: CaseInsensitiveDict[Serializer] = CaseInsensitiveDict(
    {
        "application/x-www-form-urlencoded": URLEncodedFormSerializer(),
        "application/json": JSONFormSerializer(),
        "application/octet-stream": OctetStreamSerializer(),
        "multipart/form-data": MultiPartFormSerializer(),
    }
)
