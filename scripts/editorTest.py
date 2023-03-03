from burp.api.montoya.http.message.requests import HttpRequest as BurpHttpRequest
from burp.api.montoya.http.message.responses import HttpResponse as BurpHttpResponse
from burp.api.montoya.core import ByteArray as BurpByteArray

from burpTypes import HttpRequest, ByteArray, Logging, HttpResponse
from sys import _getframe

# Test script that adds debug headers

def funName(  ):
    return _getframe(1).f_code.co_name


def request(req: HttpRequest, logger: Logging=...) -> HttpRequest | None:
    try:
        logger.logToOutput(f"Python: {funName()}() called")
        return req.withUpdatedHeader("X-Python-Intercept-Request", "Hello")
    except Exception as e:
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")
    return req


def response(res: HttpResponse, logger: Logging=...) -> HttpResponse | None:
    logger.logToOutput(f"Python: {funName()}() called")
    return res.withUpdatedHeader("X-Python-Intercept-Response", "true")

# OK
def req_edit_in_Scalpel(req: HttpRequest, logger: Logging=...) -> HttpRequest | None:
    try:
        logger.logToOutput("Python: req_edit_in_...() {funName()}() called")
        return BurpHttpRequest.httpRequest(req.toByteArray()).withAddedHeader("X-Python-In-Request-Editor", "true").toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")

# OK
def req_edit_out_Scalpel(req: HttpRequest, text: bytes, logger: Logging=...) -> HttpRequest | None:
    logger.logToOutput(f"Python: {funName()}() called")
    logger.logToOutput(f"Python: {text}")
    return BurpHttpRequest.httpRequest(ByteArray.byteArray(text)).withUpdatedHeader("X-Python-Out-Request-Editor", "true").toByteArray().getBytes()  # type: ignore

# Broken
def res_edit_in_Scalpel(res: HttpResponse, logger: Logging=...) -> HttpResponse | None:
    try:
        logger.logToOutput(f"Python: {funName()}() called")
        return BurpHttpResponse.httpResponse(res.toByteArray()).withAddedHeader("X-Python-In-Response-Editor", "true").toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"Python: {funName()}() error:\n\t{e}")

