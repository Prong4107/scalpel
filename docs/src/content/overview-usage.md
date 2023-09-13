---
title: "Usage"
menu: "overview"
menu:
    overview:
        weight: 2
---

# Usage

Scalpel allows you to programmatically intercept and modify HTTP requests/responses going through Burp, as well as creating custom request/response editors with Python.

To do so, Scalpel provides a **Burp extension GUI** for scripting and a set of **predefined function names** corresponding to specific actions:
- `[match]({{< relref "addons-api#match" >}})`: Determine whether an event will be handled by a hook.
- `[request]({{< relref "addons-api#request" >}})`: Intercept and rewrite a request.
- `[response]({{< relref "addons-api#response" >}})`: Intercept and rewrite a response.
- `[req_edit_in]({{< relref "addons-api#req_edit_in" >}})`: Create or update a request editor's content from a request.
- `[req_edit_out]({{< relref "addons-api#req_edit_out" >}})`: Update a request from an editor's modified content.
- `[res_edit_in]({{< relref "addons-api#res_edit_in" >}})`: Create or update a request editor's content from a response.
- `[res_edit_out]({{< relref "addons-api#res_edit_out" >}})`: Update a response from an editor's modified content.

-   Simply write a Python script implementing the ones you need and load the file with Scalpel Burp GUI: {{< figure src="/screenshots//choose_script.png" >}}
<!-- ^^ TODO: Better screenshot -->
## Intercept and Rewrite HTTP Traffic

-   To intercept requests/response, implement the `[request()]({{< relref "addons-api#request" >}})` and `[response()]({{< relref "addons-api#response" >}})` functions in your script:

    -   E.g: Hooks that add an arbitrary header to every requests and response:

    ```python
        from pyscalpel.http import Request, Response

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

-   Decide whether to intercept an HTTP message with the `[match()]({{< relref "addons-api#match" >}})` function:

    -   E.g: A match intercepting requests to `localhost` and `127.0.0.1` only:

        ```python
        from pyscalpel.http import Flow

        def match(flow: Flow) -> bool:
            # True if host is localhost or 127.0.0.1
            return flow.host_is("localhost", "127.0.0.1")
        ```

## Custom Burp Editors

Scalpel's main _killer feature_ is the ability to **program your own editors** using simple Python.

    -   E.g: A simple script to edit a fully URL encoded query string parameter in a request:

        ```python
        from pyscalpel.http import Request
        from pyscalpel.utils import urldecode, urlencode_all


        # Hook to initialize the editor's content
        def req_edit_in(req: Request) -> bytes | None:
            param = req.query.get("filename")
            if param is not None:
                return urldecode(param)

            # Do not modify the request
            return None

        # Hook to update the request from the editor's modified content
        def req_edit_out(req: Request, modified_content: bytes) -> Request:
            req.query["filename"] = urlencode_all(modified_content)
            return req
        ```

    -   If you open a request with a `filename` query parameter, a `Scalpel` tab should appear in the editor like shown below: {{< figure src="/screenshots/urlencode.png" >}}
    -   Once your `[req_edit_in()]({{< relref "addons-api#req_edit_in" >}})` Python hook is invoked, the tab should contain the `filename` parameter's URL decoded content. {{< figure src="/screenshots/decoded.png" >}}
    -  You can modify it to update the request and thus, include anything you want (e.g: path traversal sequences). {{< figure src="/screenshots/traversal.png" >}}
    -   When you send the request or switch to another editor tab, your Python hook `[req_edit_out()]({{< relref "addons-api#req_edit_out" >}})` will be invoked to update the parameter. {{< figure src="/screenshots/updated.png" >}}

-   You can have multiple open tabs at the same time. Just suffix your function names:

    -   E.g: Same script as above but for two parameters: "filename" and "directory".

        ```python
        from pyscalpel.http import Request
        from pyscalpel.utils import urldecode, urlencode_all

        def req_edit_in_filename(req: Request):
            param = req.query.get("filename")
            if param is not None:
                return urldecode(param)

        def req_edit_out_filename(req: Request, text: bytes):
            req.query["filename"] = urlencode_all(text)
            return req


        def req_edit_in_directory(req: Request):
            param = req.query.get("directory")
            if param is not None:
                return urldecode(param)


        def req_edit_out_directory(req: Request, text: bytes):
            req.query["directory"] = urlencode_all(text)
            return req
        ```

    -   This will result in two open tabs. One for the `filename` parameter and one for the `directory` parameter (see the second image below).
        {{< figure src="/screenshots/multiple_params.png" >}}
        {{< figure src="/screenshots/multiple_tabs.png" >}}

## Further reading

Learn more about the available hooks in the technical documentation's [Event Hooks & API]({{< relref "addons-api" >}}) section.
## Notes:

-   If your hooks return `None`, it will pass the original object without any modification

    -   If `request()` returns `None`, the original request will be forwarded without modifications.
        -   Same for `response()`
    -   If `req_edit_in()` or `res_edit_in()` returns `None`, the editor tab will not be displayed.
    -   If `req_edit_out()` or `res_edit_out()` returns `None`, the request will not be modified.
    -   If `req_edit_out()` / `res_edit_out()` isn't declared but `req_edit_in()` / `res_edit_in()` is, the corresponding editor will be **read-only**.

-   You do not have to declare every hook if you don't need them, if you only want to modify requests, you can declare the `request()` hook only.
