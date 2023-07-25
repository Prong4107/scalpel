"""Events that can be passed to the match() hook"""

from typing import Literal

EVENTS = set(
    (
        "request",
        "response",
        "req_edit_in",
        "req_edit_out",
        "res_edit_in",
        "res_edit_out",
    )
)


Events = Literal[
    "request",
    "response",
    "req_edit_in",
    "req_edit_out",
    "res_edit_in",
    "res_edit_out",
]
