from pyscalpel.http import Request, Response

# Test script that demonstrate host matching
# TODO: Add a global match callback.


def request(req: Request) -> Request | None:
    if not req.host_is("*localhost", "127.0.0.1"):
        return None

    req.headers["X-Python-Intercept-Request"] = "request"
    return req


def response(res: Response) -> Response | None:
    if not res.host_is("*localhost", "127.0.0.1"):
        return None

    res.headers["X-Python-Intercept-Response"] = "response"
    return res


def req_edit_in(req: Request) -> bytes | None:
    if not req.host_is("*localhost", "127.0.0.1"):
        return None

    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return bytes(req)


def req_edit_out(r: Request, text: bytes) -> bytes | None:
    if not r.host_is("*localhost", "127.0.0.1"):
        return None

    req = Request.from_raw(text)
    req.headers["X-Python-Out-Request-Editor"] = "req_edit_out"
    return bytes(req)


def res_edit_in(res: Response) -> bytes | None:
    if not res.host_is("*localhost", "127.0.0.1"):
        return None

    res.headers["X-Python-In-Response-Editor"] = "res_edit_in"
    return bytes(res)


def res_edit_out(r: Response, text: bytes) -> bytes | None:
    if not r.host_is("*localhost", "127.0.0.1"):
        return None

    res = Response.from_raw(text)
    res.headers["X-Python-Out-Response-Editor"] = "res_edit_out"
    return bytes(res)
