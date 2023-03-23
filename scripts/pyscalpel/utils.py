from urllib.parse import unquote_to_bytes as urllibdecode
from pyscalpel.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.burp.http_response import IHttpResponse, HttpResponse
from pyscalpel.burp.byte_array import IByteArray, ByteArray
from pyscalpel.burp.http_parameter import IHttpParameter, HttpParameter
from lexfo.scalpel import HttpMsgUtils
import pyscalpel._globals
from typing import List, TypeVar

logger = pyscalpel._globals.logger


def new_response(obj: IHttpResponse | IByteArray | bytes) -> IHttpResponse:
    # https://stackoverflow.com/a/34870210
    # TODO: Single dispatch generic functions
    try:
        obj = byte_array(obj)  # type: ignore
    except TypeError:
        pass

    return HttpResponse.httpResponse(obj)


def new_request(obj: IHttpRequest | IByteArray | bytes) -> IHttpRequest:
    try:
        obj = byte_array(obj)  # type: ignore
    except TypeError:
        pass

    return HttpRequest.httpRequest(obj)


def byte_array(_bytes: bytes) -> IByteArray:
    return ByteArray.byteArray(_bytes)


def get_bytes(array: IByteArray) -> bytes:
    return bytes(array.getBytes())


def to_bytes(obj: IHttpRequest | IHttpResponse) -> bytes:
    return get_bytes(obj.toByteArray())


def urlencode_all(bytestring: bytes) -> bytes:
    """URL Encode all bytes in the given bytes object"""
    return "".join(['%{:02X}'.format(b) for b in bytestring]).encode()


def urldecode(bytestring: bytes) -> bytes:
    """URL Decode all bytes in the given bytes object"""
    return urllibdecode(bytestring)


HttpRequestOrResponse = TypeVar(
    'HttpRequestOrResponse', IHttpRequest, IHttpResponse)


def update_header(msg: HttpRequestOrResponse, name: str, value: str) -> HttpRequestOrResponse:
    return HttpMsgUtils.updateHeader(msg, name, value)


def get_param(msg: IHttpRequest, name: str) -> IHttpParameter | None:
    params = msg.parameters()
    param = next(filter(lambda p: p.name() == name, params), None)
    return param


def new_param(name: str, value: str) -> IHttpParameter:
    return HttpParameter.urlParameter(name, value)


def update_param(req: IHttpRequest, name: str, value: str):
    return req.withUpdatedParameters([new_param(name, value)])
