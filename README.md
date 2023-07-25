# Scalpel

Scalpel is a powerful Burp Suite extension that allows you to script Burp to intercept and rewrite HTTP traffic on the fly. Its main feature is the ability to program custom Burp editors using Python. This tool is designed to provide an alternative to Burp's Java API by offering a user-friendly Python library.

## Features

-   **Scripting**: Scalpel allows you to create scripts using a GUI embedded in the Burp Suite. These scripts can be used to manipulate HTTP requests and responses in real-time.
-   **Custom Editors**: With Scalpel, you can create custom Burp editors using Python. This feature is particularly useful for interactively editing encoded or encrypted data as plaintext.
-   **Hex Editors**: Scalpel can be used to create advanced hex editors, providing a more robust solution than the native Burp Suite options.

## How It Works

Scalpel works by implementing a set of functions with well-known names. Here's an example script that adds debug headers:

```python
from pyscalpel.http import Request, Response

def request(req: Request) -> Request | None:
    req.headers["X-Python-Intercept-Request"] = "request"
    return req

def response(res: Response) -> Response | None:
    res.headers["X-Python-Intercept-Response"] = "response"
    return res

def req_edit_in(req: Request) -> bytes | None:
    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return bytes(req)

def req_edit_out(_: Request, text: bytes) -> Request | None:
    req = Request.from_raw(text)
    req.headers["X-Python-Out-Request-Editor"] = "req_edit_out"
    return req

def res_edit_in(res: Response) -> bytes | None:
    res.headers["X-Python-In-Response-Editor"] = "res_edit_in"
    return bytes(res)

def res_edit_out(_: Response, text: bytes) -> Response | None:
    res = Response.from_raw(text)
    res.headers["X-Python-Out-Response-Editor"] = "res_edit_out"
    return res
```

You can also create a hex editor by using a decorator on a hook:

```python
@editor("hex")
def req_edit_in(req: Request) -> bytes | None:
    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return bytes(req)
```

## Installation

1. Download the latest release of Scalpel from the GitHub repository.
2. Open Burp Suite and navigate to the Extender tab.
3. Click on the Add button and select the downloaded Scalpel jar file.
4. Ensure that the Scalpel extension is enabled.

## Usage

To use Scalpel, navigate to the Scalpel tab in Burp Suite. Here, you can create and manage your scripts. To create a new script, click on the 'New' button and start scripting in the provided editor.

## Contributing

Contributions to Scalpel are welcome! Please read the contributing guidelines before making any changes.

## License

Scalpel is licensed under the <on met quelle licence ?>. See the LICENSE file for more details.

## Support

If you encounter any issues or have any questions about Scalpel, please open an issue on the GitHub repository.
