from base64 import b64decode, b64encode
import binascii
from pyscalpel.http import Request, Response
from typing import cast


def req_edit_in(req: Request) -> bytes:
    if req.content:
        try:
            req.content = b64decode(req.content, validate=True)
        except binascii.Error:
            pass

    return bytes(req)


def req_edit_out(_: Request, text: bytes) -> Request:
    req = Request.from_raw(text)
    if req.content:
        req.content = b64encode(req.content)
    return req


def res_edit_in(res: Response) -> bytes:
    if res.content:
        try:
            res.content = b64decode(res.content, validate=True)
        except binascii.Error:
            pass
    return bytes(res)


def res_edit_out(_: Response, text: bytes) -> Response:
    res = Response.from_raw(text)
    if res.content:
        res.content = b64encode(res.content)
    return res
