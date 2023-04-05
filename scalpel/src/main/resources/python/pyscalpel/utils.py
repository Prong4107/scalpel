from typing import TypeVar
from pyscalpel.burp_utils import (
    IHttpParameter,
    IHttpRequest,
    get_param as burp_get_param,
    new_param,
)
from pyscalpel.http import Request


def get_param(msg: Request, name: str) -> IHttpParameter | None:
    return burp_get_param(msg.to_burp(), name)


GenericRequest = TypeVar("GenericRequest", IHttpRequest, Request)


def update_param(req: GenericRequest, name: str, value: str) -> GenericRequest:
    if isinstance(req, Request):
        return Request.from_burp(update_param(req.to_burp(), name, value))
    return req.withUpdatedParameters([new_param(name, value)])
