import inspect
from pyscalpel.burp_utils import (
    urldecode,
    urlencode_all,
)


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
    "urldecode",
    "urlencode_all",
    "current_function_name",
]
