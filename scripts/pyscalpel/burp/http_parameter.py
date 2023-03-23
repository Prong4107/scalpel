#!/usr/bin/env python
#
#  * Burp HTTP parameter able to retrieve to hold details about an HTTP request parameter.
#


from typing import Protocol
from abc import abstractmethod, ABCMeta
from burp.api.montoya.http.message.params import HttpParameter as _BurpHttpParameter

from .java_object import JavaObject


class IHttpParameter(JavaObject):
    """ generated source for interface HttpParameter """
    __metaclass__ = ABCMeta
    #
    #      * @return The parameter type.
    #

    @abstractmethod
    def type_(self):
        """ generated source for method type_ """

    #
    #      * @return The parameter name.
    #
    @abstractmethod
    def name(self) -> str:
        """ generated source for method name """

    #
    #      * @return The parameter value.
    #
    @abstractmethod
    def value(self) -> str:
        """ generated source for method value """

    #
    #      * Create a new Instance of {@code HttpParameter} with {@link HttpParameterType#URL} type.
    #      *
    #      * @param name  The parameter name.
    #      * @param value The parameter value.
    #      *
    #      * @return A new {@code HttpParameter} instance.
    #
    @abstractmethod
    def urlParameter(self, name: str, value: str) -> 'IHttpParameter':
        """ generated source for method urlParameter """

    #
    #      * Create a new Instance of {@code HttpParameter} with {@link HttpParameterType#BODY} type.
    #      *
    #      * @param name  The parameter name.
    #      * @param value The parameter value.
    #      *
    #      * @return A new {@code HttpParameter} instance.
    #
    @abstractmethod
    def bodyParameter(self, name, value):
        """ generated source for method bodyParameter """

    #
    #      * Create a new Instance of {@code HttpParameter} with {@link HttpParameterType#COOKIE} type.
    #      *
    #      * @param name  The parameter name.
    #      * @param value The parameter value.
    #      *
    #      * @return A new {@code HttpParameter} instance.
    #
    @abstractmethod
    def cookieParameter(self, name, value):
        """ generated source for method cookieParameter """

    #
    #      * Create a new Instance of {@code HttpParameter} with the specified type.
    #      *
    #      * @param name  The parameter name.
    #      * @param value The parameter value.
    #      * @param type  The header type.
    #      *
    #      * @return A new {@code HttpParameter} instance.
    #
    @abstractmethod
    def parameter(self, name, value, type_):
        """ generated source for method parameter """


HttpParameter: IHttpParameter = _BurpHttpParameter
