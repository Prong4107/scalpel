import traceback
from sys import _getframe

try: 
    from pyscalpel.utils import IHttpRequest, HttpRequest, ByteArray, logger, IHttpResponse, HttpResponse, update_header, to_bytes, new_request, new_response

    # Test script that adds debug headers
 
    # TODO: Find why httpRequest.withUpdatedHeader() is broken ?

    def fun_name(  ):
        return _getframe(1).f_code.co_name

    def request(req: IHttpRequest) -> IHttpRequest | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return update_header(req, "X-Python-Intercept-Request", fun_name())
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")


    def response(res: IHttpResponse) -> IHttpResponse | None:
        logger.logToOutput(f"Python: {fun_name()}() called")
        return update_header(res, "X-Python-Intercept-Response", fun_name())

    # OK
    def req_edit_in_Scalpel(req: IHttpRequest) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return to_bytes(update_header(req, "X-Python-In-Request-Editor", fun_name()))
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    # OK
    def req_edit_out_Scalpel(_: IHttpRequest, text: bytes) -> bytes | None:
        logger.logToOutput(f"Python: {fun_name()}() called")
        try:
            return to_bytes(update_header(new_request(text), "X-Python-Out-Request-Editor", fun_name()))
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    def res_edit_in_Scalpel(res: IHttpResponse) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return to_bytes(update_header(res, "X-Python-In-Response-Editor", fun_name()))
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    def res_edit_out_Scalpel(_: IHttpResponse, text: bytes) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            return to_bytes(update_header(new_response(text), "X-Python-Out-Response-Editor", fun_name()))
        except Exception as ex:
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

except Exception as global_ex:
    __logger__.logToOutput("Couldn't load script:")
    __logger__.logToOutput(global_ex)
    __logger__.logToOutput(traceback.format_exc())
