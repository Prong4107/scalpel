from pyscalpel.http import Request
from pyscalpel.utils import (
    urldecode,
    urlencode_all,
    get_tab_name,
)


def get_and_decode_param(req: Request, param: str) -> bytes | None:
    found = req.query.get(param)
    if found is not None:
        return urldecode(found)


def set_and_encode_param(req: Request, param: str, param_value: bytes) -> Request:
    req.query[param] = urlencode_all(param_value)
    return req


def req_edit_in_filename(req: Request) -> bytes | None:
    return get_and_decode_param(req, get_tab_name())


def req_edit_out_filename(req: Request, text: bytes) -> Request | None:
    return set_and_encode_param(req, get_tab_name(), text)


def req_edit_in_directory(req: Request) -> bytes | None:
    return get_and_decode_param(req, get_tab_name())


def req_edit_out_directory(req: Request, text: bytes) -> Request | None:
    return set_and_encode_param(req, get_tab_name(), text)
