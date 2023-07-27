import unittest
from qs import *


class TestURLParsing(unittest.TestCase):
    def test_merge(self):
        source = {"a": 1, "b": {"c": 2}}
        destination = {"a": 3, "b": {"d": 4}}
        expected = {"a": 1, "b": {"c": 2, "d": 4}}
        self.assertEqual(merge(source, destination), expected)

    def test_merge_array(self):
        source = {0: "nest", "key6": "deep"}
        destination = ["along"]
        expected = {0: "nest", "key6": "deep", 1: "along"}
        self.assertEqual(merge(source, destination), expected)

    def test_qs_parse_no_strict_no_blanks(self):
        qs = "a=1&b=2&c=3"
        expected = {"a": "1", "b": "2", "c": "3"}
        self.assertEqual(qs_parse(qs), expected)

    def test_qs_parse_with_strict(self):
        qs = "a=1&b=2&c"
        with self.assertRaises(ValueError):
            qs_parse(qs, strict_parsing=True)

    def test_qs_parse_keep_blanks(self):
        qs = "a=1&b=2&c"
        expected = {"a": "1", "b": "2", "c": ""}
        self.assertEqual(qs_parse(qs, keep_blank_values=True), expected)

    def test_simple_duplicates_wrong(self):
        qs = "a=1&a=2&a=3&a=4"

        # Mimic PHP parse_str
        expected = {"a": "4"}
        self.assertEqual(qs_parse(qs), expected)

    def test_simple_duplicates_rigth(self):
        qs = "a[]=1&a[]=2&a[]=3&a[]=4"

        # Mimic PHP parse_str
        expected = {"a": ["1", "2", "3", "4"]}
        self.assertEqual(qs_parse(qs), expected)

    def test_qs_parse_complex(self):
        qs = "key1[key2][key3][key4][]=ho&key1[key2][key3][key4][]=hey&key1[key2][key3][key4][]=choco&key1[key2][key3][key4][key5][]=nest"
        qs += "&key1[key2][key3][key4][key5][key6]=deep&key1[key2][key3][key4][key5][]=along&key1[key2][key3][key4][key5][key5_1]=hello"
        expected = {
            "key1": {
                "key2": {
                    "key3": {
                        "key4": {
                            0: "ho",
                            1: "hey",
                            2: "choco",
                            "key5": {
                                0: "nest",
                                "key6": "deep",
                                1: "along",
                                "key5_1": "hello",
                            },
                        }
                    }
                }
            }
        }
        old = self.maxDiff
        self.maxDiff = None
        output = qs_parse(qs)
        self.assertEqual(output, expected)
        self.maxDiff = old

    def test_build_qs(self):
        query = {"a": 1, "b": 2, "c": 3}
        expected = "a=1&b=2&c=3"
        self.assertEqual(build_qs(query), expected)

    def test_build_qs_nested_dict(self):
        query = {"a": 1, "b": {"c": 2, "d": 3}}
        expected = "a=1&b[c]=2&b[d]=3"
        self.assertEqual(build_qs(query), expected)

    def test_build_qs_with_list(self):
        query = {"a": 1, "b": [2, 3]}
        expected = "a=1&b[]=2&b[]=3"
        self.assertEqual(build_qs(query), expected)


class TestQSParsePairs(unittest.TestCase):
    def test_single_pair(self):
        pairs = [("a", "1")]
        expected = {"a": "1"}
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_multiple_pairs(self):
        pairs = [("a", "1"), ("b", "2"), ("c", "3")]
        expected = {"a": "1", "b": "2", "c": "3"}
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_duplicate_keys(self):
        pairs = [("a[]", "1"), ("a[]", "2"), ("a[]", "3")]
        expected = {"a": ["1", "2", "3"]}
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_blank_values(self):
        pairs = [("a", ""), ("b", ""), ("c", "3")]
        expected = {"a": "", "b": "", "c": "3"}
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_blank_values_ignore(self):
        pairs = [("a", ""), ("b", ""), ("c", "3")]
        expected = {"c": "3"}
        self.assertEqual(qs_parse_pairs(pairs, keep_blank_values=False), expected)

    # def test_strict_parsing(self):
    #     pairs = [("a", "1"), ("b", "2"), ("c", "")]
    #     with self.assertRaises(ValueError):
    #         qs_parse_pairs(pairs, strict_parsing=True)

    def test_complex_parsing(self):
        pairs = [
            ("key1[key2][key3][key4][]", "ho"),
            ("key1[key2][key3][key4][]", "hey"),
            ("key1[key2][key3][key4][]", "choco"),
            ("key1[key2][key3][key4][key5][]", "nest"),
            ("key1[key2][key3][key4][key5][key6]", "deep"),
            ("key1[key2][key3][key4][key5][]", "along"),
            ("key1[key2][key3][key4][key5][key5_1]", "hello"),
        ]
        expected = {
            "key1": {
                "key2": {
                    "key3": {
                        "key4": {
                            0: "ho",
                            1: "hey",
                            2: "choco",
                            "key5": {
                                0: "nest",
                                "key6": "deep",
                                1: "along",
                                "key5_1": "hello",
                            },
                        }
                    }
                }
            }
        }
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_complex_parsing_with_file(self):
        from base64 import b64decode

        zip_data = b64decode(
            """UEsDBBQAAAAIAFpPvlYQIK6pcAAAACMBAAAHABwAbG9sLnBocFVUCQADu6x1ZNBwd2R1eAsAAQTo
        AwAABOgDAACzsS/IKOAqSCwqTo0vLinSUM9OrTSMBhJGsSDSGEyaxEbH2mbkq+GWS63ELZmckZ+M
        Uy+QNAUpykstLsGvBkiaxdqmpKYWEDIrMSc/L52gYabxhiCH5+Tkq+soqOSXlhSUlmhacxUUZeaV
        xBdpIEQAUEsBAh4DFAAAAAgAWk++VhAgrqlwAAAAIwEAAAcAGAAAAAAAAQAAALSBAAAAAGxvbC5w
        aHBVVAUAA7usdWR1eAsAAQToAwAABOgDAABQSwUGAAAAAAEAAQBNAAAAsQAAAAAA"""
        ).decode("latin-1")

        pairs = [
            ("key1[key2][key3][key4][]", "ho"),
            ("key1[key2][key3][key4][]", "hey"),
            ("key1[key2][key3][key4][]", "choco"),
            ("key1[key2][key3][key4][key5][]", "nest"),
            ("key1[key2][key3][key4][key5][key6]", "deep"),
            ("key1[key2][key3][key4][key5][]", "along"),
            ("key1[key2][key3][key4][key5][key5_1]", "hello"),
            ("key1[key2][key3][key4][key5][key5_file]", zip_data),
        ]
        expected = {
            "key1": {
                "key2": {
                    "key3": {
                        "key4": {
                            0: "ho",
                            1: "hey",
                            2: "choco",
                            "key5": {
                                0: "nest",
                                "key6": "deep",
                                1: "along",
                                "key5_1": "hello",
                                "key5_file": zip_data,
                            },
                        }
                    }
                }
            }
        }
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_complex_parsing_weird_keys(self):
        pairs = [
            ("key_with_underscore[key*with*stars][]", "ho"),
            ("key-with-dash[key123][]", "hey"),
            ("key[key-with-dash][]", "choco"),
            ("2key[3key][4key][5key][]", "nest"),
            ("2key[3key][4key][5key][6key]", "deep"),
            ("key1[key2][key3][key4][key5][]", "along"),
            ("key1[key2][key3][key4][key5][key5_1]", "hello"),
        ]
        expected = {
            "key_with_underscore": {
                "key*with*stars": ["ho"],
            },
            "key-with-dash": {
                "key123": ["hey"],
            },
            "key": {
                "key-with-dash": ["choco"],
            },
            "2key": {
                "3key": {
                    "4key": {
                        "5key": {
                            0: "nest",
                            "6key": "deep",
                        },
                    },
                },
            },
            "key1": {
                "key2": {
                    "key3": {
                        "key4": {
                            "key5": {
                                0: "along",
                                "key5_1": "hello",
                            },
                        },
                    },
                },
            },
        }
        self.maxDiff = None
        self.assertEqual(qs_parse_pairs(pairs), expected)

    def test_is_valid_query(self):
        # Valid query names
        self.assertTrue(is_valid_php_query_name("field"))
        self.assertTrue(is_valid_php_query_name("field[key]"))
        self.assertTrue(is_valid_php_query_name("field[key1][key2]"))
        self.assertTrue(is_valid_php_query_name("field[]"))
        self.assertTrue(is_valid_php_query_name("_field"))  # Starts with underscore
        self.assertTrue(
            is_valid_php_query_name("field[key_with_underscore]")
        )  # Key with underscore
        self.assertTrue(
            is_valid_php_query_name("field[key-with-dash]")
        )  # Key with dash
        self.assertTrue(
            is_valid_php_query_name("field[key*with*stars]")
        )  # Key with stars
        self.assertTrue(
            is_valid_php_query_name("field123[key456][key789]")
        )  # Keys and field with digits
        self.assertTrue(
            is_valid_php_query_name("key1[key2][key3][key4][]")
        )  # More complex and nested field
        self.assertTrue(is_valid_php_query_name("key1[key2][key3][key4][key5][]"))
        self.assertTrue(is_valid_php_query_name("key1[key2][key3][key4][key5][key6]"))
        self.assertTrue(is_valid_php_query_name("key1[key2][key3][key4][key5][key5_1]"))
        self.assertTrue(
            is_valid_php_query_name("field[  ]")
        )  # Brackets can contain spaces
        self.assertTrue(
            is_valid_php_query_name("2field[key]")
        )  # Name can start with a number

        # Invalid query names
        self.assertFalse(
            is_valid_php_query_name("a[x]b[y]c[z]")
        )  # Can't have anything between brackets

        self.assertFalse(
            is_valid_php_query_name("[key]")
        )  # Name must not start with a bracket

        self.assertFalse(
            is_valid_php_query_name("field[key][")
        )  # Empty brackets at the end
        self.assertFalse(is_valid_php_query_name("field["))  # Incomplete bracket
        self.assertFalse(is_valid_php_query_name(""))  # Empty string
        self.assertFalse(
            is_valid_php_query_name("field[key&]")
        )  # Special character & in the key
        self.assertFalse(
            is_valid_php_query_name("field[key&key]")
        )  # Special character & in the key


if __name__ == "__main__":
    unittest.main()
