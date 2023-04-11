from typing import cast, Type, TypeVar
from functools import cache
from pyscalpel.java.object import JavaObject


@cache
def _is_pdoc() -> bool:
    from sys import modules  # pylint: disable=import-outside-toplevel

    return "pdoc" in modules


ExpectedObject = TypeVar("ExpectedObject")


def import_java(
    module: str, name: str, expected_type: Type[ExpectedObject] = JavaObject
) -> ExpectedObject:
    """Import a Java class using Python's import mechanism.

    :param module: The module to import from. (e.g. "java.lang")
    :param name: The name of the class to import. (e.g. "String")
    :param expected_type: The expected type of the class. (e.g. JavaObject)
    :return: The imported class.
    """
    if _is_pdoc():
        return cast(expected_type, None)  # type: ignore
    try:
        module = __import__(module, fromlist=[name])
        return getattr(module, name)
    except ImportError as exc:
        raise ImportError(f"Could not import Java class {name}") from exc
