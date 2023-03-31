#!/usr/bin/env python
#
#  * Burp HTTP header able to retrieve to hold details about an HTTP header.
#

from typing import Protocol
from abc import abstractmethod, ABCMeta
from burp.api.montoya.http.message import HttpHeader as _BurpHttpHeader
from .java_object import JavaObject
from functools import singledispatch


class IHttpHeader(JavaObject):
    """ generated source for interface HttpHeader """
    __metaclass__ = ABCMeta
    #
    #      * @return The name of the header.
    #

    @abstractmethod
    @staticmethod
    def name() -> str:
        """ generated source for method name """

    #
    #      * @return The value of the header.
    #
    @abstractmethod
    @staticmethod
    def value() -> str:
        """ generated source for method value """

    #
    #      * @return The {@code String} representation of the header.
    #
    @abstractmethod
    def __str__(self):
        """ generated source for method toString """

    #
    #      * Create a new instance of {@code HttpHeader} from name and value.
    #      *
    #      * @param name  The name of the header.
    #      * @param value The value of the header.
    #      *
    #      * @return A new {@code HttpHeader} instance.
    #
    @abstractmethod
    @singledispatch
    def httpHeader(self, name: str, value: str) -> 'IHttpHeader':
        """ generated source for method httpHeader """

    #
    #      * Create a new instance of HttpHeader from a {@code String} header representation.
    #      * It will be parsed according to the HTTP/1.1 specification for headers.
    #      *
    #      * @param header The {@code String} header representation.
    #      *
    #      * @return A new {@code HttpHeader} instance.
    #
    @abstractmethod
    @httpHeader.register(str)
    def httpHeader_0(self, header: str) -> 'IHttpHeader':
        """ generated source for method httpHeader_0 """):


HttpHeader: IHttpHeader=_BurpHttpHeader
