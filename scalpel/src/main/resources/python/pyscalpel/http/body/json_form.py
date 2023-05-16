from __future__ import annotations


from collections.abc import Mapping

import json


from pyscalpel.http.body.abstract import FormSerializer


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
