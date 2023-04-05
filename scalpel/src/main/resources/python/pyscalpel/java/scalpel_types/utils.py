# pylint: disable=invalid-name

from typing import cast, TypeVar
from abc import ABCMeta, abstractmethod
from lexfo.scalpel import PythonUtils as _PythonUtils  # pylint: disable=import-error # type: ignore
from pyscalpel.java.object import JavaObject
from pyscalpel.java.bytes import JavaBytes
from pyscalpel.java.burp.http_request import IHttpRequest
from pyscalpel.java.burp.http_response import IHttpResponse
from pyscalpel.java.burp.byte_array import IByteArray

RequestOrResponse = TypeVar("RequestOrResponse", bound=IHttpRequest | IHttpResponse)


class IPythonUtils(JavaObject):
    __metaclass__ = ABCMeta

    @abstractmethod
    def toPythonBytes(self, java_bytes: JavaBytes) -> list[int]:
        pass

    @abstractmethod
    def toJavaBytes(self, python_bytes: bytes | list[int] | bytearray) -> JavaBytes:
        pass

    @abstractmethod
    def toByteArray(self, python_bytes: bytes | list[int] | bytearray) -> IByteArray:
        pass

    @abstractmethod
    def getClassName(self, msg: JavaObject) -> str:
        pass

    @abstractmethod
    def updateHeader(self, msg: RequestOrResponse, name: str, value: str) -> RequestOrResponse:
        pass


PythonUtils: IPythonUtils = cast(IPythonUtils, _PythonUtils)
