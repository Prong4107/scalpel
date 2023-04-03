from pyscalpel.utils import IHttpRequest, IHttpResponse, update_header, to_bytes, new_request, new_response

# Test script that adds debug headers
    

def request(req: IHttpRequest) -> IHttpRequest | None:
    return update_header(req, "X-Python-Intercept-Request", __name__)


def response(res: IHttpResponse) -> IHttpResponse | None:
    return update_header(res, "X-Python-Intercept-Response", __name__)


def req_edit_in(req: IHttpRequest) -> bytes | None:
    return to_bytes(update_header(req, "X-Python-In-Request-Editor", __name__))


def req_edit_out(_: IHttpRequest, text: bytes) -> bytes | None:
    return to_bytes(update_header(new_request(text), "X-Python-Out-Request-Editor", __name__))


def res_edit_in(res: IHttpResponse) -> bytes | None:
    return to_bytes(update_header(res, "X-Python-In-Response-Editor", __name__))


def res_edit_out(_: IHttpResponse, text: bytes) -> bytes | None:
    return to_bytes(update_header(new_response(text), "X-Python-Out-Response-Editor", __name__))
