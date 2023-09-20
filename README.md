# Scalpel

Scalpel is a powerful **Burp Suite** extension that allows you to script Burp in order to intercept, rewrite HTTP traffic on the fly, and program custom Burp editors in Python 3.

It provides an interactive way to edit encoded/encrypted data as plaintext and offers an easy-to-use Python library as an alternative to Burp's Java API.

## Features

-   **Python Library**: Easy-to-use Python library, especially welcome for non-Java developers.

-   **Intercept and Rewrite HTTP Traffic**: Scalpel provides a set of predefined function names that can be implemented to intercept and modify HTTP requests and responses.

-   **Custom Burp Editors**: Program your own Burp editors in Python. Encoded/encrypted data can be handled as plaintext.

    -   **Hex Editors**: Ability to create improved hex editors.

<br>

## Usage

Scalpel provides a Burp extension GUI for scripting and a set of predefined function names corresponding to specific actions. Simply write a Python script implementing the ones you need.

Below is an example script:

```py
from pyscalpel.http import Request, Response, Flow

# Hook to determine whether an event should be handled by a hook
def match(flow: Flow) -> bool:
    return flow.host_is("localhost")

# Hook to intercept and rewrite a request
def request(req: Request) -> Request | None:
    req.headers["X-Python-Intercept-Request"] = "request"
    return req

# Hook to intercept and rewrite a response
def response(res: Response) -> Response | None:
    res.headers["X-Python-Intercept-Response"] = "response"
    return res

# Hook to create or update a request editor's content from a request
def req_edit_in(req: Request) -> bytes | None:
    req.headers["X-Python-In-Request-Editor"] = "req_edit_in"
    return bytes(req)

# Hook to update a request from an editor's modified content
def req_edit_out(_: Request, text: bytes) -> Request | None:
    req = Request.from_raw(text)
    req.headers["X-Python-Out-Request-Editor"] = "req_edit_out"
    return req

# Hook to create or update a response editor's content from a response
def res_edit_in(res: Response) -> bytes | None:
    res.headers["X-Python-In-Response-Editor"] = "res_edit_in"
    return bytes(res)

# Hook to update a response from an editor's modified content
def res_edit_out(_: Response, text: bytes) -> Response | None:
    res = Response.from_raw(text)
    res.headers["X-Python-Out-Response-Editor"] = "res_edit_out"
    return res
```

## Documentation

User documentation is available [**here**](https://prong4107.github.io/scalpel/public).

## Examples

Example scripts are available in the [`examples/`](examples/) directory of the project.

<br>

## Requirements

Scalpel is compatible with Windows, Linux and MacOS.

-   OpenJDK >= `17`
-   Python >= `3.10`
-   pip
-   venv

### Debian-based distributions

The following packages are required:

```sh
sudo apt install build-essential python3 python3-dev openjdk-17-jdk
```

### Windows

Microsoft Visual C++ >=14.0 is required:
https://visualstudio.microsoft.com/visual-cpp-build-tools/

## Installation

Download the latest JAR release of Scalpel from [GitHub](https://github.com/Prong4107/scalpel/releases).

The release file has to be added to Burp Suite as an extension. Learn more in the [documentation](https://prong4107.github.io/scalpel/public/overview-installation/).

<br>

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Scalpel is licensed under [Apache License 2.0](LICENCE.MD).

## Contact

For any questions or feedback, please open an issue or contact the [maintainer](mailto:n.maccary@lexfo.fr).
