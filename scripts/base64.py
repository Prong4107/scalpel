import traceback
from sys import _getframe
try:
    from scalpel.burp.http_request import IHttpRequest, HttpRequest
    from scalpel.burp.http_response import IHttpResponse, HttpResponse
    from scalpel.burp.byte_array import IByteArray, ByteArray
    from scalpel.burp.logging import Logging
    from scalpel.utils import new_response, byte_array, to_bytes, get_bytes, new_request, logger
    from base64 import b64decode, b64encode


    # Test script that adds debug headers

    # TODO: Find why httpRequest.withUpdatedHeader() is broken ?

    def fun_name(  ):
        return _getframe(1).f_code.co_name

    def req_edit_in_Scalpel(req: IHttpRequest):
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            body_bytes = b64decode(get_bytes(req.body()))
            new_body = byte_array(body_bytes)
            new_req = req.withBody(new_body)
            new_bytes = to_bytes(new_req)
            return new_bytes
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")
            
    def req_edit_out_Scalpel(req: IHttpRequest, text: bytes):
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            req = new_request(bytes(text))
            body_bytes = b64encode(get_bytes(req.body()))
            new_body = byte_array(body_bytes)
            new_req = req.withBody(new_body)
            new_bytes = to_bytes(new_req)
            return new_bytes
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    def res_edit_in_Scalpel(res: IHttpResponse) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            body_bytes = b64decode(get_bytes(res.body()))
            new_body = byte_array(body_bytes)
            new_res = res.withBody(new_body)
            new_bytes = to_bytes(new_res)
            return new_bytes
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")
except Exception as global_ex:
    __logger__.logToOutput("Couldn't load script:")
    __logger__.logToOutput(global_ex)
    __logger__.logToOutput(traceback.format_exc())
