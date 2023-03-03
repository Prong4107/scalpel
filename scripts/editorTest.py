from burp.api.montoya.http.message.requests import HttpRequest as _BurpHttpRequest
from burp.api.montoya.http.message.responses import HttpResponse as _BurpHttpResponse
from burp.api.montoya.core import ByteArray as _BurpByteArray

from burpTypes import HttpRequest, ByteArray, Logging, HttpResponse
from sys import _getframe
import traceback


BurpHttpRequest: HttpRequest = _BurpHttpRequest
BurpHttpResponse: HttpResponse = _BurpHttpResponse
BurpByteArray: ByteArray = _BurpByteArray

# Test script that adds debug headers

# TODO: Find why httpRequest.withUpdatedHeader() is broken ?

def funName(  ):
    return _getframe(1).f_code.co_name

def request(req: HttpRequest, logger: Logging=...) -> HttpRequest | None:
    try:
        logger.logToOutput(f"Python: {funName()}() called")
        return req.withAddedHeader("X-Python-Intercept-Request", funName())
    except Exception as e:
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")


def response(res: HttpResponse, logger: Logging=...) -> HttpResponse | None:
    logger.logToOutput(f"Python: {funName()}() called")
    return res.withUpdatedHeader("X-Python-Intercept-Response", funName())

# OK
def req_edit_in_Scalpel(req: HttpRequest, logger: Logging=...) -> bytes | None:
    try:
        logger.logToOutput(f"Python: {funName()}() called")
        return BurpHttpRequest.httpRequest(req.toByteArray()).withAddedHeader("X-Python-In-Request-Editor", funName()).toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")

# OK
def req_edit_out_Scalpel(req: HttpRequest, text: bytes, logger: Logging=...) -> bytes | None:
    logger.logToOutput(f"Python: {funName()}() called")
    try:
        return BurpHttpRequest.httpRequest(BurpByteArray.byteArray(text)).withAddedHeader("X-Python-Out-Request-Editor", funName()) .toByteArray().getBytes() # type: ignore
    except Exception as e:
        logger.logToOutput(traceback.format_exc())
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")

def res_edit_in_Scalpel(res: HttpResponse, logger: Logging=...) -> bytes | None:
    try:
        logger.logToOutput(f"Python: {funName()}() called")
        return BurpHttpResponse.httpResponse(res.toByteArray()).withAddedHeader("X-Python-In-Response-Editor", funName()).toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")

