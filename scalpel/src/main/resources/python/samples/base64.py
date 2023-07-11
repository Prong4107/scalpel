from base64 import b64decode, b64encode
import binascii
from pyscalpel.http import Request, Response


def req_edit_in(req: Request) -> bytes:
    if req.content:
        try:
            req.content = b64decode(req.content, validate=True)
        except binascii.Error:
            pass

    return req.content or b""


def req_edit_out(req: Request, text: bytes) -> Request:
    if req.content:
        req.content = b64encode(text)
    return req


def res_edit_in(res: Response) -> bytes:
    if res.content:
        try:
            res.content = b64decode(res.content, validate=True)
        except binascii.Error:
            pass
    return bytes(res)


def res_edit_out(res: Response, text: bytes) -> Response:
    if res.content:
        res.content = b64encode(text)
    return res
