from pyscalpel.http import Request, Response

# Test script that adds debug headers


def request(req: Request) -> Request | None:
    req.headers["X-Python-Intercept-Request"] = "request"
    req.headers.set_all(b"X-Duplicate-Header", [b"duplicate111", b"duplicate222    |"])
    return req


def response(res: Response) -> Response | None:
    res.headers["X-Python-Intercept-Response"] = "response"
    return res


def req_edit_in(req: Request) -> bytes | None:
    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return bytes(req)


def req_edit_out(_: Request, text: bytes) -> bytes | None:
    req = Request.from_raw(text)
    req.headers["X-Python-Out-Request-Editor"] = "req_edit_out"
    return bytes(req)


def res_edit_in(res: Response) -> bytes | None:
    res.headers["X-Python-In-Response-Editor"] = "res_edit_in"
    return bytes(res)


def res_edit_out(_: Response, text: bytes) -> bytes | None:
    res = Response.from_raw(text)
    res.headers["X-Python-Out-Response-Editor"] = "res_edit_out"
    return bytes(res)
