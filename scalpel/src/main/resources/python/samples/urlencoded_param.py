from pyscalpel.http import Request
from pyscalpel.utils import get_param, urldecode, urlencode_all, update_param

# POC script to edit a fully URL encoded parameter

PARAM_NAME = "filename"


def req_edit_in(req: Request) -> bytes | None:
    param = get_param(req, PARAM_NAME)
    if param is not None:
        return urldecode(str.encode(param.value()))


def req_edit_out(req: Request, text: bytes) -> bytes | None:
    return bytes(update_param(req, PARAM_NAME, urlencode_all(text)))
