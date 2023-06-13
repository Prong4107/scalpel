from typing import TypeVar
import inspect
from pyscalpel.burp_utils import (
    IHttpParameter,
    IHttpRequest,
    get_param as burp_get_param,
    new_param,
    urldecode,
    urlencode_all,
)
from pyscalpel.http import Request
from pyscalpel.encoding import always_str


def get_param(msg: Request, name: str) -> IHttpParameter | None:
    return burp_get_param(msg.to_burp(), name)


GenericRequest = TypeVar("GenericRequest", IHttpRequest, Request)


def update_param(
    req: GenericRequest, name: str | bytes, value: str | bytes
) -> GenericRequest:
    if isinstance(req, Request):
        return Request.from_burp(update_param(req.to_burp(), name, value))
    return req.withUpdatedParameters([new_param(always_str(name), always_str(value))])


def current_function_name() -> str:
    """Get current function name

    Returns:
        str: The function name
    """
    frame = inspect.currentframe()
    if frame is None:
        return ""

    caller_frame = frame.f_back
    if caller_frame is None:
        return ""

    return caller_frame.f_code.co_name


def get_tab_name() -> str:
    """Get current editor tab name

    Returns:
        str: The tab name
    """
    frame = inspect.currentframe()
    prefixes = ("req_edit_in", "req_edit_out")

    # Go to previous frame till the editor name is found
    while frame is not None:
        frame_name = frame.f_code.co_name
        for prefix in prefixes:
            if frame_name.startswith(prefix):
                return frame_name.removeprefix(prefix).removeprefix("_")
        frame = frame.f_back

    raise RuntimeError("get_tab_name() wasn't called from an editor callback.")


__all__ = [
    "get_param",
    "update_param",
    "urldecode",
    "urlencode_all",
    "current_function_name",
]
