# Scalpel

Scalpel is a powerful Burp Suite extension that allows you to script Burp and intercept, rewrite HTTP traffic on the fly, and program custom Burp editors using Python.

It provides an interactive way to edit encoded/encrypted data as plaintext and offers an easy-to-use Python library as an alternative to Burp's Java API.

## Features

-   **Intercept and Rewrite HTTP Traffic**: Scalpel provides a set of predefined function names that you can implement to intercept and modify HTTP requests and responses.

-   **Custom Burp Editors**: You can program custom Burp editors using Python. This feature allows you to handle encoded or encrypted data as plaintext.

-   **Python Library**: Scalpel provides a Python library that is easier to use than Burp's Java API.

-   **Hex Editor**: Scalpel can create better hex editors than the native one.

## Requirements

-   Python >= 3.10
-   JDK >= 17

## Installation

To use Scalpel, you need to have Python >= 3.10 and any JDK >= 17 installed on your machine.

You can download the latest release of Scalpel from the GitHub releases page.

The release file is a .jar file that you can add to your Burp Suite as an extension.

## Usage

Scalpel works by creating a script with the GUI the extension embeds in Burp, and implementing a set of functions with predefined names.

Here is an example script:

```py
from pyscalpel.http import Request, Response

# Hook to intercept and rewrite a request
def request(req: Request) -> Request | None:
    req.headers["X-Python-Intercept-Request"] = "request"
    return req

# Hook to intercept and rewrite a response
def response(res: Response) -> Response | None:
    res.headers["X-Python-Intercept-Response"] = "response"
    return res

# Hook to generate a request editor's content from a request
def req_edit_in(req: Request) -> bytes | None:
    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return bytes(req)

# Hook to update a request from an editor's edited content
def req_edit_out(_: Request, text: bytes) -> Request | None:
    req = Request.from_raw(text)
    req.headers["X-Python-Out-Request-Editor"] = "req_edit_out"
    return req

# Hook to generate a response editor's content from a response
def res_edit_in(res: Response) -> bytes | None:
    res.headers["X-Python-In-Response-Editor"] = "res_edit_in"
    return bytes(res)

# Hook to update a response from an editor's edited content
def res_edit_out(_: Response, text: bytes) -> Response | None:
    res = Response.from_raw(text)
    res.headers["X-Python-Out-Response-Editor"] = "res_edit_out"
    return res
```

## Documentation

User documentation is available at [[INSERT DOCUMENTATION LINK]](http://userdoc.scalpel.com).

## Examples

Example scripts are available in the `examples/` directory of the project.

## Building

The project can be built using `./gradlew build`, which generates a .jar file in `./scalpel/build/libs/scalpel-0.0.1.jar`.

## Testing

Unit tests can be run with `./run_tests.sh`.

## Compatibility

Scalpel is compatible with Windows and Linux.

## Contributing

Pull requests are welcome!

## License

Scalpel is licensed under [INSERT LICENSE HERE].

## Contact

For any questions or feedback, please create an issue or contact [INSERT CONTACT INFORMATION HERE].
