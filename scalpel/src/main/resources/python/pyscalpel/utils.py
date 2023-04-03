from .burp_utils import (
    IHttpParameter,
    IHttpRequest,
    HttpParameter,
    urldecode,
    urlencode_all,
    get_param as burp_get_param,
    new_param,
    update_param as burp_update_param,
    to_bytes,
    get_bytes,
    logger,
)

from typing import TypeVar
from functools import singledispatch
from .http import Request


def get_param(msg: Request, name: str) -> IHttpParameter | None:
    return burp_get_param(msg.to_burp(), name)


GenericRequest = TypeVar("GenericRequest", IHttpRequest, Request)


def update_param(req: GenericRequest, name: str, value: str) -> GenericRequest:
    if isinstance(req, Request):
        return Request.from_burp(update_param(req.to_burp(), name, value))
    return req.withUpdatedParameters([new_param(name, value)])
