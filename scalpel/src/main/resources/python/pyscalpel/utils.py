from typing import TypeVar
from pyscalpel.burp_utils import (
    IHttpParameter,
    IHttpRequest,
    get_param as burp_get_param,
    new_param,
    urldecode,
    urlencode_all,
)
from pyscalpel.http import Request
from pyscalpel.encoding import always_str

def get_param(msg: Request, name: str) -> IHttpParameter | None:
    return burp_get_param(msg.to_burp(), name)


GenericRequest = TypeVar("GenericRequest", IHttpRequest, Request)


def update_param(req: GenericRequest, name: str | bytes, value: str | bytes) -> GenericRequest:
    if isinstance(req, Request):
        return Request.from_burp(update_param(req.to_burp(), name, value))
    return req.withUpdatedParameters([new_param(always_str(name), always_str(value))])

__all__ = [ "get_param", "update_param" , "urldecode", "urlencode_all" ]
