#!/usr/bin/env python
from abc import abstractmethod, ABCMeta, ABC
from functools import singledispatch


class JavaObject(ABC):

    """ generated source for class Object """
    @abstractmethod
    def __init__(self):
        """ generated source for method __init__ """
    @abstractmethod
    def getClass(self):
        """ generated source for method getClass """
    @abstractmethod
    def hashCode(self) -> int:
        """ generated source for method hashCode """
    @abstractmethod
    def equals(self, obj) -> bool:
        """ generated source for method equals """
        return False

    @abstractmethod
    def clone(self) -> 'JavaObject':
        """ generated source for method clone """
    @abstractmethod
    def __str__(self) -> str:
        """ generated source for method toString """
    @abstractmethod
    def notify(self) -> None:
        """ generated source for method notify """
    @abstractmethod
    def notifyAll(self) -> None:
        """ generated source for method notifyAll """

    @singledispatch
    @abstractmethod
    def wait(self) -> None:
        """ generated source for method wait """

    @abstractmethod
    @wait.register(int)
    def wait_0(self, arg0) -> None:
        """ generated source for method wait_0 """

    @abstractmethod
    @wait.register(int)
    def wait_1(self, timeoutMillis: int, nanos: int) -> None:
        """ generated source for method wait_1 """
    @abstractmethod
    def finalize(self) -> None:
        """ generated source for method finalize """
