from pyscalpel.http import Request
from pyscalpel.utils import urldecode, urlencode_all


def req_edit_in_filename(req: Request) -> bytes | None:
    param = req.query.get("filename")
    if param is not None:
        return urldecode(param)


def req_edit_out_filename(req: Request, text: bytes) -> Request | None:
    req.query["filename"] = urlencode_all(text)
    return req


def req_edit_in_directory(req: Request) -> bytes | None:
    param = req.query.get("directory")
    if param is not None:
        return urldecode(param)


def req_edit_out_directory(req: Request, text: bytes) -> Request | None:
    req.query["directory"] = urlencode_all(text)
    return req
