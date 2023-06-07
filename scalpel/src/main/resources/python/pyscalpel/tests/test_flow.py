import unittest

from pyscalpel.http.flow import *


class FlowTestCase(unittest.TestCase):
    def test_construct_default(self):
        flow = Flow()

        self.assertEqual("http", flow.scheme)
        self.assertEqual("", flow.host)
        self.assertEqual(0, flow.port)
        self.assertEqual(None, flow.request)
        self.assertEqual(None, flow.response)
        self.assertEqual(None, flow.text)

    def test_construct(self):
        request = Request.make("GET", "https://localhost")
        response = Response.make(200)
        flow = Flow(
            scheme="https",
            host="localhost",
            port=443,
            request=request,
            response=response,
            text=b"Hello world!",
        )

        self.assertEqual("https", flow.scheme)
        self.assertEqual("localhost", flow.host)
        self.assertEqual(443, flow.port)
        self.assertEqual(request, flow.request)
        self.assertEqual(response, flow.response)
        self.assertEqual(b"Hello world!", flow.text)


if __name__ == "__main__":
    unittest.main()
