from pyscalpel.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.burp.http_response import IHttpResponse, HttpResponse
from pyscalpel.burp.byte_array import IByteArray, ByteArray
from lexfo.scalpel import HttpMsgUtils
import pyscalpel._globals
from typing import List, TypeVar

logger = pyscalpel._globals.logger

 

def new_response(obj: IHttpResponse | IByteArray | bytes) -> IHttpResponse:
    if isinstance(obj, bytes):
        obj = byte_array(obj)

    return HttpResponse.httpResponse(obj)

def new_request(obj: IHttpRequest | IByteArray | bytes) -> IHttpRequest:
    if isinstance(obj, bytes):
        obj = byte_array(obj)
 
    return HttpRequest.httpRequest(obj)

def byte_array(_bytes: bytes) -> IByteArray:
    return ByteArray.byteArray(_bytes)

def get_bytes(array: IByteArray) -> bytes:
    return bytes(array.getBytes())

def to_bytes(obj: IHttpRequest | IHttpResponse) -> bytes:
    return get_bytes(obj.toByteArray())


T = TypeVar('T', IHttpRequest, IHttpResponse)

def update_header(msg: T, name: str, value: str) -> T:
    return HttpMsgUtils.updateHeader(msg, name, value)
