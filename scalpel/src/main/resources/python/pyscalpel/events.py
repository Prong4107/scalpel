from typing import Literal

EVENTS = (
    "request",
    "response",
    "req_edit_in",
    "req_edit_out",
    "res_edit_in",
    "res_edit_out",
)


Events = Literal[
    "request",
    "response",
    "req_edit_in",
    "req_edit_out",
    "res_edit_in",
    "res_edit_out",
]
