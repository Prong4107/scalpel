from typing import TypedDict
from pyscalpel.java.object import JavaObject
from pyscalpel.java.burp.logging import Logging
from typing import Any


class Context(TypedDict):
    """Scalpel Python execution context"""

    API: Any
    """
        The Burp [Montoya API]
        (https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html)
        root object.

        Allows you to interact with Burp by directly manipulating the Java object.
    
    """

    directory: str
    """The framework directory"""

    logger: Logging
    """The logging object to use to display logs in Burp GUI"""

    user_script: str
    """The loaded script path"""

    framework: str
    """The framework (loader script) path"""

    venv: str
    """The venv the script was loaded in"""
