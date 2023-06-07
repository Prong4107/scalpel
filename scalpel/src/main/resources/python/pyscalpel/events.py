from typing import Literal

EVENTS = (
    "request",
    "response",
    "edit_request_in",
    "edit_request_out",
    "edit_response_in",
    "edit_response_out",
)


Events = Literal[
    "request",
    "response",
    "edit_request_in",
    "edit_request_out",
    "edit_response_in",
    "edit_response_out",
]
