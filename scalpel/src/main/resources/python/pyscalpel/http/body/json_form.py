from __future__ import annotations


from collections.abc import Mapping

import json
from typing import Literal, cast
import string

from pyscalpel.http.body.abstract import (
    FormSerializer,
    TupleExportedForm,
    ExportedForm,
    DictExportedForm,
)


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


class PrintableJsonEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, bytes):
            return "".join(
                ch if ch in string.printable else "\\u{:04x}".format(ord(ch))
                for ch in obj.decode("latin-1")
            )
        return super().default(obj)


def json_convert(value) -> JSON_VALUE_TYPES:
    return json.loads(json.dumps(value, cls=PrintableJsonEncoder))


class JSONFormSerializer(FormSerializer):
    def serialize(
        self, deserialized_body: Mapping[JSON_KEY_TYPES, JSON_VALUE_TYPES], req=...
    ) -> bytes:
        return json.dumps(deserialized_body).encode("utf-8")

    def deserialize(self, body: bytes, req=...) -> JSONForm | None:
        try:
            parsed = json.loads(body)
        except json.JSONDecodeError:
            return None

        return JSONForm(parsed) if isinstance(parsed, dict) else None

    def get_empty_form(self, req=...) -> JSONForm:
        return JSONForm()

    def deserialized_type(self) -> type[JSONForm]:
        return JSONForm

    def export_form_to_tuple(self, source: JSONForm) -> TupleExportedForm:
        # Roughly convert non scalar values to string
        exported: list[
            tuple[
                str | bytes | int | bool | float,
                str | bytes | int | bool | float | None,
            ]
        ] = list()
        for key, val in source.items():
            match val:
                case int() | bool() | float() | str() | bytes() | None:
                    exported.append((key, val))
                case _:
                    str_val = json.dumps(val)
                    exported.append((key, str_val))

        return tuple(exported)

    def export_form_to_dict(self, source: JSONForm) -> DictExportedForm:
        # Form is already a plain dict
        return cast(DictExportedForm, dict(source))

    def prefered_imports(self) -> set[Literal["dict"]]:
        return set(("dict",))

    def prefered_exports(self) -> set[Literal["dict"]]:
        return set(("dict",))

    def import_form(self, exported: ExportedForm, req=...) -> JSONForm:
        match exported:
            case dict():
                # Convert bytes values to string
                # handle non printable using \u
                #   https://www.json.org/json-en.html
                return JSONForm(
                    cast(dict[JSON_KEY_TYPES, JSON_VALUE_TYPES], json_convert(exported))
                )
            case tuple():
                return JSONForm(
                    (cast(JSON_KEY_TYPES, json_convert(key)), json_convert(value))
                    for key, value in exported
                )
