import traceback
from sys import _getframe
__logger__.logToOutput("Hello");
try:
    from pyscalpel.utils import IHttpRequest, HttpRequest, ByteArray, logger, IHttpResponse, HttpResponse

    # Test script that adds debug headers

    # TODO: Find why httpRequest.withUpdatedHeader() is broken ?

    def fun_name(  ):
        return _getframe(1).f_code.co_name

    def request(req: IHttpRequest) -> IHttpRequest | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return req.withAddedHeader("X-Python-Intercept-Request", fun_name())
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")


    def response(res: IHttpResponse) -> IHttpResponse | None:
        logger.logToOutput(f"Python: {fun_name()}() called")
        return res.withAddedHeader("X-Python-Intercept-Response", fun_name()).withUpdatedHeader("ETagZ", "LOLZ")

    # OK
    def req_edit_in_Scalpel(req: IHttpRequest) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return HttpRequest.httpRequest(req.toByteArray()).withAddedHeader("X-Python-In-Request-Editor", fun_name()).toByteArray().getBytes()
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    # OK
    def req_edit_out_Scalpel(_: IHttpRequest, text: bytes) -> bytes | None:
        logger.logToOutput(f"Python: {fun_name()}() called")
        try:
            return HttpRequest.httpRequest(ByteArray.byteArray(text)).withAddedHeader("X-Python-Out-Request-Editor", fun_name()) .toByteArray().getBytes() # type: ignore
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    def res_edit_in_Scalpel(res: IHttpResponse) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return HttpResponse.httpResponse(res.toByteArray()).withAddedHeader("X-Python-In-Response-Editor", fun_name()).toByteArray().getBytes()
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    def res_edit_out_Scalpel(_: IHttpResponse, text: bytes) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return HttpResponse.httpResponse(ByteArray.byteArray(text)).withAddedHeader("X-Python-Out-Response-Editor", fun_name()) .toByteArray().getBytes() # type: ignore
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

except Exception as global_ex:
    __logger__.logToOutput("Couldn't load script:")
    __logger__.logToOutput(global_ex)
    __logger__.logToOutput(traceback.format_exc())
