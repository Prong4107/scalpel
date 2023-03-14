from scalpel.burp.http_request import IHttpRequest, HttpRequest
from scalpel.burp.http_response import IHttpResponse, HttpResponse
from scalpel.burp.byte_array import IByteArray, ByteArray
import scalpel._globals

logger = scalpel._globals.logger

def new_response(res: IHttpResponse) -> IHttpResponse:
    return HttpResponse.httpResponse(res)

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

