from pyscalpel.java.burp.http_request import IHttpRequest
from pyscalpel.java.burp.http_response import IHttpResponse
from pyscalpel.burp_utils import byte_array, to_bytes, get_bytes, new_request, new_response
from base64 import b64decode, b64encode
import binascii


def req_edit_in(req: IHttpRequest) -> bytes:
    try:
        body_bytes = b64decode(get_bytes(req.body()), validate=True)
    except binascii.Error:
        return to_bytes(req)

    new_body = byte_array(body_bytes)
    new_req = req.withBody(new_body)
    new_bytes = to_bytes(new_req)
    return new_bytes


def req_edit_out(_: IHttpRequest, text: bytes) -> bytes:
    req = new_request(text)
    body_bytes = b64encode(get_bytes(req.body()))
    new_body = byte_array(body_bytes)
    new_req = req.withBody(new_body)
    new_bytes = to_bytes(new_req)
    return new_bytes


def res_edit_in(res: IHttpResponse) -> bytes:
    try:
        body_bytes = b64decode(get_bytes(res.body()), validate=True)
    except binascii.Error:
        return to_bytes(res)

    new_body = byte_array(body_bytes)
    new_res = res.withBody(new_body)
    new_bytes = to_bytes(new_res)
    return new_bytes


def res_edit_out(_: IHttpResponse, text: bytes) -> bytes:
    res = new_response(text)
    body_bytes = b64encode(get_bytes(res.body()))
    new_body = byte_array(body_bytes)
    new_res = res.withBody(new_body)
    new_bytes = to_bytes(new_res)
    return new_bytes
