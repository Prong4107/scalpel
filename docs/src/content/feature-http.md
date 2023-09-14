---
title: "Intercept and rewrite HTTP traffic"
menu: "features"
menu:
    features:
        weight: 1
---

# Intercept and Rewrite HTTP Traffic

#### Request / Response

To intercept requests/responses, implement the [`request()`]({{< relref "addons-api#request" >}}) and [`response()`]({{< relref "addons-api#response" >}}) functions in your script:

_E.g: Hooks that add an arbitrary header to every request and response:_

```python
from pyscalpel import Request, Response

# Intercept the request
def request(req: Request) -> Request:
    # Add an header
    req.headers["X-Python-Intercept-Request"] = "request"
    # Return the modified request
    return req

# Same for response
def response(res: Response) -> Response:
    res.headers["X-Python-Intercept-Response"] = "response"
    return res
```

<br>

#### Match

Decide whether to intercept an HTTP message with the [`match()`]({{< relref "addons-api#match" >}}) function:

_E.g: A match intercepting requests to `localhost` and `127.0.0.1` only:_

```python
from pyscalpel.http import Flow

def match(flow: Flow) -> bool:
    # True if host is localhost or 127.0.0.1
    return flow.host_is("localhost", "127.0.0.1")
```

## Further reading

Learn more about the available hooks in the technical documentation's [Event Hooks & API]({{< relref "addons-api" >}}) section.
