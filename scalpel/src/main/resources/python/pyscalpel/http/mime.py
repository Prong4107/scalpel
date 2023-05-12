import re
import unittest
from urllib.parse import quote as urllibquote
from requests_toolbelt.multipart.encoder import MultipartEncoder, encode_with
from typing import Sequence


def parse_mime_header_value(header_str: str) -> list[tuple[str, str]]:
    params = list()
    if header_str is not None:
        # Match key=value pairs where value can be in double quotes
        param_pattern = re.compile(r'([\w-]+)\s*=\s*(".*?"|[^;]*)(?:;|$)')
        matches = param_pattern.finditer(header_str)
        for match in matches:
            key, value = match.group(1), match.group(2)
            params.append(
                (
                    key,
                    value.strip('"'),
                )
            )
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


class ParseMimeHeaderTestCase(unittest.TestCase):
    def test_empty_string(self):
        header_str = ""
        result = parse_mime_header_value(header_str)
        self.assertEqual(result, [])

    def test_single_pair_no_quotes(self):
        header_str = "key=value"
        result = parse_mime_header_value(header_str)
        expected = [("key", "value")]
        self.assertEqual(result, expected)

    def test_single_pair_with_quotes(self):
        header_str = 'key="value"'
        result = parse_mime_header_value(header_str)
        expected = [("key", "value")]
        self.assertEqual(result, expected)

    def test_multiple_pairs(self):
        header_str = "key1=value1; key2=value2; key3=value3"
        result = parse_mime_header_value(header_str)
        expected = [("key1", "value1"), ("key2", "value2"), ("key3", "value3")]
        self.assertEqual(result, expected)

    def test_mixed_quotes(self):
        header_str = 'key1="value1"; key2=value2; key3="value3"'
        result = parse_mime_header_value(header_str)
        expected = [("key1", "value1"), ("key2", "value2"), ("key3", "value3")]
        self.assertEqual(result, expected)

    def test_value_with_semicolon_and_quotes(self):
        header_str = 'key="value;with;semicolons"; key2=value2'
        result = parse_mime_header_value(header_str)
        expected = [("key", "value;with;semicolons"), ("key2", "value2")]
        self.assertEqual(result, expected)


class HeaderParsingTestCase(unittest.TestCase):
    def test_unparse_header_value(self):
        parsed_header = [
            ("Content-Type", "text/html"),
            ("charset", "utf-8"),
            ("boundary", "abcdef"),
        ]
        expected = 'text/html; charset="utf-8"; boundary="abcdef"'
        result = unparse_header_value(parsed_header)
        self.assertEqual(result, expected)

    def test_unparse_header_value_single_parameter(self):
        parsed_header = [
            ("Content-Type", "text/html"),
            ("charset", "utf-8"),
        ]
        expected = 'text/html; charset="utf-8"'
        result = unparse_header_value(parsed_header)
        self.assertEqual(result, expected)

    def test_parse_header(self):
        key = "Content-Type"
        value = 'text/html; charset="utf-8"; boundary="abcdef"'
        expected = [
            ("Content-Type", 'text/html; charset="utf-8"; boundary="abcdef"'),
            ("charset", "utf-8"),
            ("boundary", "abcdef"),
        ]
        result = parse_header(key, value)
        self.assertEqual(result, expected)

    def test_parse_header_single_parameter(self):
        key = "Content-Type"
        value = 'text/html; charset="utf-8"'
        expected = [
            ("Content-Type", 'text/html; charset="utf-8"'),
            ("charset", "utf-8"),
        ]
        result = parse_header(key, value)
        self.assertEqual(result, expected)


class BoundaryExtractionTestCase(unittest.TestCase):
    def test_extract_boundary(self):
        content_type = 'multipart/form-data; boundary="abcdefg12345"'
        encoding = "utf-8"
        expected = b"abcdefg12345"
        result = extract_boundary(content_type, encoding)
        self.assertEqual(result, expected)

    def test_extract_boundary_no_quotes(self):
        content_type = "multipart/form-data; boundary=abcdefg12345"
        encoding = "utf-8"
        expected = b"abcdefg12345"
        result = extract_boundary(content_type, encoding)
        self.assertEqual(result, expected)

    def test_extract_boundary_multiple_parameters(self):
        content_type = 'multipart/form-data; charset=utf-8; boundary="abcdefg12345"'
        encoding = "utf-8"
        expected = b"abcdefg12345"
        result = extract_boundary(content_type, encoding)
        self.assertEqual(result, expected)

    def test_extract_boundary_missing_boundary(self):
        content_type = "multipart/form-data; charset=utf-8"
        encoding = "utf-8"
        with self.assertRaises(RuntimeError):
            extract_boundary(content_type, encoding)

    def test_extract_boundary_unexpected_mimetype(self):
        content_type = 'application/json; boundary="abcdefg12345"'
        encoding = "utf-8"
        with self.assertRaises(RuntimeError):
            extract_boundary(content_type, encoding)


class TestHeaderParams(unittest.TestCase):
    def setUp(self):
        self.params: Sequence[tuple[str, str | None]] = [
            ("Content-Disposition", "form-data"),
            ("name", "file"),
            ("filename", "index.html"),
        ]

    def test_find_header_param(self):
        # Testing existing key
        self.assertEqual(find_header_param(self.params, "name"), ("name", "file"))

        self.assertEqual(
            find_header_param(self.params, "filename"), ("filename", "index.html")
        )

        # Testing non-existing key
        self.assertEqual(find_header_param(self.params, "non-existing-key"), None)

    def test_update_header_param(self):
        # Testing update of existing key
        updated_params = update_header_param(self.params, "name", "updated_file")
        self.assertIn(("name", "updated_file"), updated_params)

        # Testing addition of new key
        updated_params = update_header_param(self.params, "new-key", "new-value")
        self.assertIn(("new-key", "new-value"), updated_params)

        # Testing update with None value
        updated_params = update_header_param(self.params, "name", None)
        self.assertIn(("name", None), updated_params)


if __name__ == "__main__":
    unittest.main()
