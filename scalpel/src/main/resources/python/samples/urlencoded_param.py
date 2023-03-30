from pyscalpel.java.burp.http_request import IHttpRequest
# from pyscalpel.java.burp.http_response import IHttpResponse
from pyscalpel.utils import to_bytes, get_param, urldecode, urlencode_all, update_param

# POC script to edit a fully URL encoded parameter

param_name = "username"


def req_edit_in(req: IHttpRequest) -> bytes | None:
    param = get_param(req, param_name)
    if param is not None:
        text_bytes = str.encode(param.value())
        return urldecode(text_bytes)


def req_edit_out(req: IHttpRequest, text: bytes) -> bytes | None:
    encoded = urlencode_all(text)
    str_encoded = str(encoded, "ascii")
    new_req = update_param(req, param_name, str_encoded)
    return to_bytes(new_req)
