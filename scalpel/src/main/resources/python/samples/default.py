from pyscalpel.http import Request, Response

# Test script that adds debug headers


def request(req: Request) -> Request | None:
    req.headers["X-Python-Intercept-Request"] = "request"
    return req


def response(res: Response) -> Response | None:
    res.headers["X-Python-Intercept-Response"] = "response"
    return res


def req_edit_in(req: Request) -> bytes | None:
    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return req.to_bytes()


def req_edit_out(_: Request, text: bytes) -> bytes | None:
    req = Request.from_bytes(text)
    req.headers["X-Python-Out-Request-Editor"] = "req_edit_out"
    return req.to_bytes()


def res_edit_in(res: Response) -> bytes | None:
    res.headers["X-Python-In-Response-Editor"] = "res_edit_in"
    return res.to_bytes()


def res_edit_out(_: Response, text: bytes) -> bytes | None:
    res = Response.from_bytes(text)
    res.headers["X-Python-Out-Response-Editor"] = "res_edit_out"
    return res.to_bytes()
