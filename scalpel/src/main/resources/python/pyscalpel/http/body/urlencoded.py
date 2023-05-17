from __future__ import annotations
from collections.abc import Mapping

import urllib.parse
import qs

from typing import Sequence, Any, Mapping, Literal
from mitmproxy.coretypes import multidict


from pyscalpel.encoding import always_bytes, always_str
from pyscalpel.http.body.abstract import (
    ExportedForm,
)

from .abstract import FormSerializer, ExportedForm


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


def convert_for_urlencode(val: str | float | bool | bytes | int):
    match val:
        case bytes():
            return val.decode()
        case bool():
            return "1" if val else "0"
        case _:
            return str(val)


class URLEncodedFormSerializer(FormSerializer):
    def serialize(
        self, deserialized_body: multidict.MultiDict[bytes, bytes], req=...
    ) -> bytes:
        return b"&".join(b"=".join(field) for field in deserialized_body.items())

    def deserialize(self, body: bytes, req=...) -> QueryParams:
        fields = urllib.parse.parse_qsl(body)
        return QueryParams(fields)

    def get_empty_form(self, req=...) -> Any:
        return QueryParams(tuple())

    def deserialized_type(self) -> type[QueryParams]:
        return QueryParams

    def import_form(self, exported: ExportedForm, req=...) -> QueryParams:
        match exported:
            case tuple():
                fields = list()
                for key, val in exported:
                    # Skip null values
                    if val is None:
                        continue

                    # Convert key,val as str
                    fields.append(
                        (convert_for_urlencode(key), convert_for_urlencode(val))
                    )
                return QueryParams(fields)
            case dict():
                mapped_qs = qs.build_qs(exported)
                return self.deserialize(mapped_qs.encode())

    def export_form_to_tuple(self, source: QueryParams) -> ExportedForm:
        return source.fields

    def export_form_to_dict(self, source: QueryParams) -> Mapping:
        serialized = self.serialize(source)
        parsed = qs.qs_parse(serialized.decode())
        return parsed

    def prefered_exports(self) -> set[Literal["dict", "tuple"]]:
        return set(("dict", "tuple"))

    def prefered_imports(self) -> set[Literal["dict", "tuple"]]:
        return set(("dict", "tuple"))
