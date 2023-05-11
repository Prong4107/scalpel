from mitmproxy.utils import strutils
from urllib.parse import unquote_to_bytes as urllibdecode


# str/bytes conversion helpers from mitmproxy/http.py:
# https://github.com/mitmproxy/mitmproxy/blob/main/mitmproxy/http.py#:~:text=def-,_native,-(x%3A
def always_bytes(x: str | bytes | int) -> bytes:
    if isinstance(x, int):
        x = str(x)
    return strutils.always_bytes(x, "utf-8", "surrogateescape")


def always_str(x: str | bytes | int) -> str:
    if isinstance(x, int):
        return str(x)
    return strutils.always_str(x, "utf-8", "surrogateescape")


def native(x: bytes) -> str:
    # While headers _should_ be ASCII, it's not uncommon for certain headers to be utf-8 encoded.
    return x.decode("utf-8", "surrogateescape")


def urlencode_all(data: bytes | str) -> bytes:
    """URL Encode all bytes in the given bytes object"""
    return "".join(f"%{b:02X}" for b in always_bytes(data)).encode()


def urldecode(data: bytes | str) -> bytes:
    """URL Decode all bytes in the given bytes object"""
    return urllibdecode(always_bytes(data))
