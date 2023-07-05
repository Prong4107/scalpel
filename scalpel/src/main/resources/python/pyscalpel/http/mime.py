import re
from urllib.parse import quote as urllibquote
from requests_toolbelt.multipart.encoder import MultipartEncoder, encode_with
from typing import Sequence


def parse_mime_header_value(header_str: str) -> list[tuple[str, str]]:
    params = list()
    if header_str is not None:
        inside_quotes = False
        start = 0
        for i, char in enumerate(header_str):
            if char == '"':
                inside_quotes = not inside_quotes
            elif char == ";" and not inside_quotes:
                pair = header_str[start:i]
                split_pair = pair.split("=", 1)
                if len(split_pair) == 2:  # Check if there is a key-value pair
                    key, value = split_pair[0].strip(), split_pair[1].strip()
                    value = value.strip('"')
                    params.append((key, value))
                start = i + 1
        pair = header_str[start:]
        split_pair = pair.split("=", 1)
        if len(split_pair) == 2:  # Check if there is a key-value pair
            key, value = split_pair[0].strip(), split_pair[1].strip()
            value = value.strip('"')
            params.append((key, value))
    return params


def unparse_header_value(parsed_header: list[tuple[str, str]]) -> str:
    """Creates a header value from a list like: [(header key, header value), (parameter1 key, parameter1 value), ...]

    Args:
        parsed_header (list[tuple[str, str]]): List containers header key, header value and parameters as tuples

    Returns:
        _type_: A header value (doesn't include the key)
    """
    # email library doesn't allow to set multiple MIME parameters so we have to do it ourselves.
    assert len(parsed_header) >= 2
    header_value: str = parsed_header[0][1]
    for param_key, param_value in parsed_header[1:]:
        quoted_value = urllibquote(param_value)
        header_value += f'; {param_key}="{quoted_value}"'

    return header_value


def parse_header(key: str, value: str) -> list[tuple[str, str]]:
    parsed_header = parse_mime_header_value(value)
    parsed_header.insert(
        0,
        (
            key,
            value,
        ),
    )
    return parsed_header


# Taken from requests_toolbelt
def _split_on_find(content, bound):
    point = content.find(bound)
    return content[:point], content[point + len(bound) :]


# Taken from requests_toolbelt
def extract_boundary(content_type: str, encoding: str) -> bytes:
    ct_info = tuple(x.strip() for x in content_type.split(";"))
    mimetype = ct_info[0]
    if mimetype.split("/")[0].lower() != "multipart":
        raise RuntimeError(f"Unexpected mimetype in content-type: '{mimetype}'")
    for item in ct_info[1:]:
        attr, value = _split_on_find(item, "=")
        if attr.lower() == "boundary":
            return encode_with(value.strip('"'), encoding)
    raise RuntimeError("Missing boundary in content-type header")


def find_header_param(
    params: Sequence[tuple[str, str | None]], key: str
) -> tuple[str, str | None] | None:
    try:
        return next(param for param in params if param[0] == key)
    except StopIteration:
        return None


def update_header_param(
    params: Sequence[tuple[str, str | None]], key: str, value: str | None
) -> list[tuple[str, str | None]]:
    """Copy the provided params and update or add the matching value"""
    new_params: list[tuple[str, str | None]] = list()
    found: bool = False
    for pkey, pvalue in params:
        if not found and key == pkey:
            pvalue = value
            found = True
        new_params.append((pkey, pvalue))

    if not found:
        new_params.append((key, value))

    return new_params


def get_header_value_without_params(header_value: str) -> str:
    return header_value.split(";", maxsplit=1)[0].strip()
