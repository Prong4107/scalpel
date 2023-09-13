import unittest
from pyscalpel.http.response import *


class ResponseTestCase(unittest.TestCase):
    def test_from_mitmproxy(self):
        mitmproxy_response = MITMProxyResponse.make(
            200,
            b"Hello World!",
            Headers([(b"Content-Type", b"text/html")]),
        )
        response = Response.from_mitmproxy(mitmproxy_response)

        self.assertEqual("HTTP/1.1", response.http_version)
        self.assertEqual(200, response.status_code)
        self.assertEqual("OK", response.reason)

        # TODO: Add an update_content_length flag like in Request.
        #       (requires dropping mitmproxy and writting from scratch)
        del response.headers["Content-Length"]
        self.assertEqual(Headers([(b"Content-Type", b"text/html")]), response.headers)
        self.assertEqual(b"Hello World!", response.content)
        self.assertIsNone(response.trailers)

    def test_make(self):
        response = Response.make(
            status_code=200,
            content=b"Hello World!",
            headers=Headers([(b"Content-Type", b"text/html")]),
            host="localhost",
            port=8080,
            scheme="http",
        )

        # TODO: Add an update_content_length flag like in Request.
        #       (requires dropping mitmproxy and writting from scratch)
        del response.headers["Content-Length"]

        self.assertEqual("HTTP/1.1", response.http_version)
        self.assertEqual(200, response.status_code)
        self.assertEqual("OK", response.reason)
        self.assertEqual(Headers([(b"Content-Type", b"text/html")]), response.headers)
        self.assertEqual(b"Hello World!", response.content)
        self.assertIsNone(response.trailers)
        self.assertEqual("http", response.scheme)
        self.assertEqual("localhost", response.host)
        self.assertEqual(8080, response.port)

    def test_host_is(self):
        response = Response.make(200)
        response.host = "example.com"

        self.assertTrue(response.host_is("example.com"))
        self.assertFalse(response.host_is("google.com"))


if __name__ == "__main__":
    unittest.main()
