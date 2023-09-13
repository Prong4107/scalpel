import unittest
from pyscalpel.encoding import *


class TestUtilsModule(unittest.TestCase):
    def test_always_bytes(self):
        # Test with str input
        data_str = "test"
        result_str = always_bytes(data_str)
        expected_str = b"test"
        self.assertEqual(result_str, expected_str)

        # Test with bytes input
        data_bytes = b"test"
        result_bytes = always_bytes(data_bytes)
        expected_bytes = b"test"
        self.assertEqual(result_bytes, expected_bytes)

        # Test with int input
        data_int = 123
        result_int = always_bytes(data_int)
        expected_int = b"123"
        self.assertEqual(result_int, expected_int)

    def test_always_str(self):
        # Test with str input
        data_str = "test"
        result_str = always_str(data_str)
        expected_str = "test"
        self.assertEqual(result_str, expected_str)

        # Test with bytes input
        data_bytes = b"test"
        result_bytes = always_str(data_bytes)
        expected_bytes = "test"
        self.assertEqual(result_bytes, expected_bytes)

        # Test with int input
        data_int = 123
        result_int = always_str(data_int)
        expected_int = "123"
        self.assertEqual(result_int, expected_int)

    def test_native(self):
        # Test with ASCII bytes input
        data_bytes_ascii = b"test"
        result_bytes_ascii = native(data_bytes_ascii)
        expected_bytes_ascii = "test"
        self.assertEqual(result_bytes_ascii, expected_bytes_ascii)

        # Test with UTF-8 bytes input
        data_bytes_utf8 = b"\xc3\xa4\xc3\xb6\xc3\xbc"
        result_bytes_utf8 = native(data_bytes_utf8)
        expected_bytes_utf8 = "äöü"
        self.assertEqual(result_bytes_utf8, expected_bytes_utf8)

    def test_urlencode_all(self):
        # Test with bytes input
        data_bytes = b"test"
        result_bytes = urlencode_all(data_bytes)
        expected_bytes = b"%74%65%73%74"
        self.assertEqual(result_bytes, expected_bytes)

        # Test with str input
        data_str = "äöü"
        result_str = urlencode_all(data_str)
        expected_str = b"%C3%A4%C3%B6%C3%BC"
        self.assertEqual(result_str, expected_str)

    def test_urldecode(self):
        # Test with bytes input
        data_bytes = b"%74%65%73%74"
        result_bytes = urldecode(data_bytes)
        expected_bytes = b"test"
        self.assertEqual(result_bytes, expected_bytes)

        # Test with str input
        data_str = "%C3%A4%C3%B6%C3%BC"
        result_str = urldecode(data_str)
        expected_str = b"\xc3\xa4\xc3\xb6\xc3\xbc"
        self.assertEqual(result_str, expected_str)


if __name__ == "__main__":
    unittest.main()
