from __future__ import annotations


from typing import Literal

from pyscalpel.http.body.abstract import *
from pyscalpel.http.body.json_form import *
from pyscalpel.http.body.multipart import *
from pyscalpel.http.body.urlencoded import *

IMPLEMENTED_CONTENT_TYPES = (
    "application/x-www-form-urlencoded",
    "application/json",
    "multipart/form-data",
)

# In Python 3.11 it should be possible to do
#   IMPLEMENTED_CONTENT_TYPES_TP = Type[*IMPLEMENTED_CONTENT_TYPES]
ImplementedContentTypesTp = Literal[
    "application/x-www-form-urlencoded", "application/json", "multipart/form-data"
]

CONTENT_TYPE_TO_SERIALIZER: dict[ImplementedContentTypesTp, FormSerializer] = {
    "application/x-www-form-urlencoded": URLEncodedFormSerializer(),
    "application/json": JSONFormSerializer(),
    "multipart/form-data": MultiPartFormSerializer(),
}
