from ..burp.java_object import JavaObject
from ..burp.java_bytes import JavaBytes
from typing import List, cast, TypeVar
from abc import ABCMeta, abstractmethod
from lexfo.scalpel import PythonUtils as _PythonUtils
from ..burp.http_request import IHttpRequest
from ..burp.http_response import IHttpResponse

RequestOrResponse = TypeVar(
    "RequestOrResponse", bound=IHttpRequest | IHttpResponse)


class IPythonUtils(JavaObject):
    __metaclass__ = ABCMeta

    @abstractmethod
    def toPythonBytes(self, java_bytes: JavaBytes) -> list[int]:
        pass

    @abstractmethod
    def toJavaBytes(self, python_bytes: bytes | list[int] | bytearray) -> JavaBytes:
        pass

    @abstractmethod
    def getClassName(self, msg: JavaObject) -> str:
        pass

    @abstractmethod
    def updateHeader(self, msg: RequestOrResponse, name: str, value: str) -> RequestOrResponse:
        pass


PythonUtils: IPythonUtils = cast(IPythonUtils, _PythonUtils)
