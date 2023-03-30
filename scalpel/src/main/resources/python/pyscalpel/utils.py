from urllib.parse import unquote_to_bytes as urllibdecode
from pyscalpel.java.burp.http_request import IHttpRequest, HttpRequest
from pyscalpel.java.burp.http_response import IHttpResponse, HttpResponse
from pyscalpel.java.burp.byte_array import IByteArray, ByteArray
from pyscalpel.java.burp.http_parameter import IHttpParameter, HttpParameter
from lexfo.scalpel import PythonUtils
import pyscalpel._globals
from pyscalpel.java.burp.java_bytes import JavaBytes
from typing import List, TypeVar, cast
from collections.abc import Iterable
# from pyscalpel.java.scalpel_types import PythonUtils

logger = pyscalpel._globals.logger

HttpRequestOrResponse = TypeVar(
    'HttpRequestOrResponse', IHttpRequest, IHttpResponse)

ByteArraySerialisable = TypeVar(
    'ByteArraySerialisable', IHttpRequest, IHttpResponse)

ByteArrayConvertible = TypeVar(
    'ByteArrayConvertible', bytes, JavaBytes, list[int], str)


def new_response(obj: IHttpResponse | IByteArray | ByteArrayConvertible) -> IHttpResponse:
    # https://stackoverflow.com/a/34870210
    # TODO: Single dispatch generic functions
    try:
        obj = byte_array(obj)  # type: ignore
    except TypeError:
        pass

    return HttpResponse.httpResponse(obj)


def new_request(obj: IHttpRequest | IByteArray | bytes | JavaBytes) -> IHttpRequest:
    try:
        obj = byte_array(obj)  # type: ignore
    except TypeError:
        pass

    return HttpRequest.httpRequest(obj)


def byte_array(_bytes: ByteArrayConvertible) -> IByteArray:
    # Handle buggy bytes casting
    if isinstance(_bytes, (bytes)):
        # This is needed because Python will _sometimes_ try
        #   to interpret bytes as a an integer when passing to ByteArray.byteArray() and crash like this:
        #       TypeError: Error converting parameter 1: 'bytes' object cannot be interpreted as an integer
        #
        # Restarting Burp fixes the issue when it happens, so to avoid unstable behaviour
        #   we explcitely convert the bytes to a PyJArray of Java byte
        cast_value = cast(JavaBytes, PythonUtils.toJavaBytes(_bytes))
        return ByteArray.byteArray(cast_value)

    return ByteArray.byteArray(_bytes)


def get_bytes(array: IByteArray) -> bytes:
    return to_bytes(array.getBytes())


def to_bytes(obj: ByteArraySerialisable | JavaBytes) -> bytes:
    # Handle java signed bytes
    if isinstance(obj, Iterable):
        # Convert java signed bytes to python unsigned bytes
        return bytes([b & 0xff for b in cast(JavaBytes, obj)])

    return get_bytes(cast(ByteArraySerialisable, obj).toByteArray())


def urlencode_all(bytestring: bytes) -> bytes:
    """URL Encode all bytes in the given bytes object"""
    return "".join(['%{:02X}'.format(b) for b in bytestring]).encode()


def urldecode(bytestring: bytes) -> bytes:
    """URL Decode all bytes in the given bytes object"""
    return urllibdecode(bytestring)


def update_header(msg: HttpRequestOrResponse, name: str, value: str) -> HttpRequestOrResponse:
    return PythonUtils.updateHeader(msg, name, value)


def get_param(msg: IHttpRequest, name: str) -> IHttpParameter | None:
    params = msg.parameters()
    param = next(filter(lambda p: p.name() == name, params), None)
    return param


def new_param(name: str, value: str) -> IHttpParameter:
    return HttpParameter.urlParameter(name, value)


def update_param(req: IHttpRequest, name: str, value: str):
    return req.withUpdatedParameters([new_param(name, value)])
