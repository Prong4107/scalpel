from typing import TypedDict
from pyscalpel.java.object import JavaObject
from pyscalpel.java.burp.logging import Logging


class Scalpel(TypedDict):
    API: JavaObject
    file: str
    directory: str
    logger: Logging
    user_script: str
    framework: str
    venv: str
