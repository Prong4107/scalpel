import traceback
from sys import _getframe


def fun_name():
    return _getframe(1).f_code.co_name


try:
    from pyscalpel.burp.http_request import IHttpRequest, HttpRequest
    from pyscalpel.burp.http_response import IHttpResponse, HttpResponse
    from pyscalpel.burp.byte_array import IByteArray, ByteArray
    from pyscalpel.burp.logging import Logging
    from pyscalpel.utils import new_response, byte_array, to_bytes, get_bytes, new_request, logger, get_param, urldecode, urlencode_all, update_param, to_bytes
    from base64 import b64decode, b64encode

    # POC script to edit a fully URL encoded parameter

    param_name = "username"

    def req_edit_in_Scalpel(req: IHttpRequest) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            param = get_param(req, param_name)
            if param is None:
                return None

            text_bytes: bytes = str.encode(param.value())
            return urldecode(text_bytes)
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

    def req_edit_out_Scalpel(req: IHttpRequest, text: bytes) -> bytes | None:
        try:
            logger.logToOutput(f"Python: {fun_name()}() called")
            encoded = urlencode_all(bytes(text))
            str_encoded = str(encoded, "ascii")
            new_req = update_param(req, param_name, str_encoded)
            return to_bytes(new_req)
        except Exception as ex:
            logger.logToOutput(traceback.format_exc())
            logger.logToError(f"Python: {fun_name()}() error:\n\t{ex}")

except Exception as global_ex:
    __logger__.logToOutput("Couldn't load script:")
    __logger__.logToOutput(global_ex)
    __logger__.logToOutput(traceback.format_exc())
