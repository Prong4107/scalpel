from pyscalpel.http import Request
from pyscalpel.utils import urldecode, urlencode_all

# POC script to edit a fully URL encoded parameter

PARAM_NAME = "filename"


def req_edit_in_filename(req: Request) -> bytes | None:
    param = req.query.get(PARAM_NAME)
    if param is not None:
        return urldecode(param)


def req_edit_out_filename(req: Request, text: bytes) -> Request | None:
    req.query[PARAM_NAME] = urlencode_all(text)
    return req
