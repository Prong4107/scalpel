from __future__ import annotations

from typing import Protocol, TypeVar, Sequence, Any, TypeAlias, Literal
from abc import ABC, abstractmethod, ABCMeta

from collections.abc import MutableMapping, Mapping

from pyscalpel.http.headers import Headers


# Multipart needs the Content-Type header for the boundary parameter
# So Serializer needs an object that references the header
# This is used as a Forward declaration
class ObjectWithHeaders(Protocol):
    headers: Headers


KT = TypeVar("KT")
VT = TypeVar("VT")


class Form(MutableMapping[KT, VT], metaclass=ABCMeta):
    pass


Scalars: TypeAlias = str | bytes | int | bool | float

TupleExportedForm: TypeAlias = tuple[
    tuple[Scalars, Scalars | None],
    ...,
]

DictExportedForm: TypeAlias = dict[Scalars, Scalars | None]

ExportedForm: TypeAlias = DictExportedForm | TupleExportedForm


# Abstract base class
class FormSerializer(ABC):
    @abstractmethod
    def serialize(self, deserialized_body: Form, req: ObjectWithHeaders) -> bytes:
        """Serialize a parsed form to raw bytes

        Args:
            deserialized_body (Form): The parsed form
            req (ObjectWithHeaders): The originating request (used for multipart to get an up to date boundary from content-type)

        Returns:
            bytes: Form's raw bytes representation
        """

    @abstractmethod
    def deserialize(self, body: bytes, req: ObjectWithHeaders) -> Form | None:
        """Parses the form from it's raw bytes representation

        Args:
            body (bytes): The form as bytes
            req (ObjectWithHeaders): The originating request  (used for multipart to get an up to date boundary from content-type)

        Returns:
            Form | None: The parsed form
        """

    @abstractmethod
    def get_empty_form(self, req: ObjectWithHeaders) -> Form:
        """Get an empty parsed form object

        Args:
            req (ObjectWithHeaders): The originating request (used to get a boundary for multipart forms)

        Returns:
            Form: The empty form
        """

    @abstractmethod
    def deserialized_type(self) -> type[Form]:
        """Gets the form concrete type

        Returns:
            type[Form]: The form concrete type
        """

    @abstractmethod
    def prefered_imports(self) -> set[Literal["tuple"] | Literal["dict"]]:
        """Get the prefered import formats
        Some formats map well to dict (JSON) so they prefer it.
        Some formats do not map to dict (e.g: allowed duplicates key) so they prefer a (key,val) tuple
        Some formats can both map to dict and tuple so they specify both
            (e.g.: PHP style urlencoded maps to dict, but can also uses duplicates keys in other contexts)
        The conversion algorithm/format is chosen depending on the best pair (prefered_import,prefered_export)
        """

    # It is possible to hardcode the conversion cases in Request
    # (i.e. checking if the former serializer is JSON and next is URLEncoded and changing the conversion
    #   algorithm depending on the result, but Request should be serializer agnostic so that an user can
    #   implement their own serializer for their own form data format
    #   and have it work without modifying any library code.

    #   Pair ranking:
    #
    # 1: (tuple,tuple) -> tuple
    # 2: (dict,dict) -> dict (e.g. JSON/URLEncoded)
    # 3: (dict,tuple) -> tuple
    # 4: (tuple,dict) -> tuple

    @abstractmethod
    def prefered_exports(self) -> set[Literal["tuple"] | Literal["dict"]]:
        """Get the prefered export formats
        Some formats map well to dict (JSON) so they prefer it.
        Some formats may not map to dict (e.g: allowed duplicates key) so they prefer a (key,val) tuple
        Some formats may both map to dict and tuple so they specify both
            (e.g.: PHP style urlencoded maps to dict, but can also uses duplicates keys in other contexts)
        The conversion algorithm/format is chosen depending on the best pair (prefered_import,prefered_export)
        """

    @abstractmethod
    def import_form(self, exported: ExportedForm, req: ObjectWithHeaders) -> Form:
        """Imports a form exported by a serializer
            Used to convert a form from a Content-Type to another
            Information may be lost in the process

        Args:
            exported (ExportedForm): The exported form
            req: (ObjectWithHeaders): Used to get multipart boundary

        Returns:
            Form: The form converted to this serializer's format
        """

    @abstractmethod
    def export_form_to_tuple(self, source: Form) -> TupleExportedForm:
        """Formats a form so it can be imported by another serializer
            Information may be lost in the process

        Args:
            form (Form): The form to export

        Returns:
            ExportedForm: The exported form
        """

    @abstractmethod
    def export_form_to_dict(self, source: Form) -> Mapping[Scalars, Scalars | None]:
        """Formats a form so it can be imported by another serializer
            Enforces the type to be dict, used when

        Args:
            form (Form): The form to export

        Returns:
            ExportedForm: The exported form
        """
