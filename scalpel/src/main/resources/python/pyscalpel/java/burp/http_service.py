from burp.api.montoya.http import HttpService as _BurpHttpService
from abc import abstractmethod, ABCMeta
from .http_message import IHttpMessage
from .byte_array import IByteArray
from .http_parameter import IHttpParameter
from .http_header import IHttpHeader
from .java_object import JavaObject
from functools import singledispatchmethod
from typing import overload, NoReturn


class IHttpService(JavaObject, metaclass=ABCMeta):
    @abstractmethod
    def host(self) -> str:
        """The hostname or IP address for the service."""

    @abstractmethod
    @overload
    def httpService(self, baseUrl: str) -> "IHttpService":
        """Create a new instance of {@code HttpService} from a base URL."""
        ...

    @abstractmethod
    @overload
    def httpService(self, baseUrl: str, secure: bool) -> "IHttpService":
        """Create a new instance of {@code HttpService} from a base URL and a protocol."""
        ...

    @abstractmethod
    @overload
    def httpService(self, host: str, port: int, secure: bool) -> "IHttpService":
        """Create a new instance of {@code HttpService} from a host, a port and a protocol."""
        ...

    @abstractmethod
    def httpService(self, *args, **kwargs) -> "IHttpService":
        """Create a new instance of {@code HttpService} from a host, a port and a protocol."""
        ...

    @abstractmethod
    def port(self) -> int:
        """The port number for the service."""

    @abstractmethod
    def secure(self) -> bool:
        """True if a secure protocol is used for the connection, false otherwise."""

    @abstractmethod
    def __str__(self) -> str:
        """The {@code String} representation of the service."""


HttpService: IHttpService = _BurpHttpService
