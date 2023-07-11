"""
    This module declares type definitions used for Java objects.
    
    If you are a normal user, you should probably never have to manipulate these objects yourself.
"""
from .bytes import JavaBytes
from .import_java import import_java
from .object import JavaClass, JavaObject

__all__ = ["import_java", "JavaObject", "JavaBytes", "JavaClass"]
