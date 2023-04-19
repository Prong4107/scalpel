from base64 import b64decode, b64encode
import binascii
from pyscalpel.http import Request, Response
from typing import cast


def req_edit_in(req: Request) -> bytes:
    if req.content:
        try:
            req.content = b64decode(cast(bytes, req.get_content()), validate=True)
        except binascii.Error:
            pass

    return bytes(req)


def req_edit_out(_: Request, text: bytes) -> bytes:
    req = Request.from_bytes(text)
    if req.content:
        req.content = b64encode(cast(bytes, req.get_content()))
    return bytes(req)


def res_edit_in(res: Response) -> bytes:
    if res.content:
        try:
            res.content = b64decode(cast(bytes, res.get_content()), validate=True)
        except binascii.Error:
            pass
    return bytes(res)


def res_edit_out(_: Response, text: bytes) -> bytes:
    res = Response.from_bytes(text)
    if res.content:
        res.content = b64encode(cast(bytes, res.get_content()))
    return bytes(res)
