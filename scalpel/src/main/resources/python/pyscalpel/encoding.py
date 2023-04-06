from mitmproxy.utils import strutils

# str/bytes conversion helpers from mitmproxy/http.py:
# https://github.com/mitmproxy/mitmproxy/blob/main/mitmproxy/http.py#:~:text=def-,_native,-(x%3A
def always_bytes(x: str | bytes) -> bytes:
    return strutils.always_bytes(x, "utf-8", "surrogateescape")

def always_str(x: str | bytes) -> str:
    return strutils.always_str(x, "utf-8", "surrogateescape")

def native(x: bytes) -> str:
    # While headers _should_ be ASCII, it's not uncommon for certain headers to be utf-8 encoded.
    return x.decode("utf-8", "surrogateescape")
