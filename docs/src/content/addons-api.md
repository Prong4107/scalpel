---
title: "Event Hooks & API"
url: "api/events.html"
aliases:
    - /addons-events/
layout: single
menu:
    addons:
        weight: 1
---

# Event Hooks

Scalpel scripts hook into Burps's internal mechanisms through **event hooks**.

These are implemented as methods with a set of well-known names.
Events receive `Request`, `Response`, `Flow` and `bytes` objects as arguments. By modifying these objects, scripts can
change traffic on the fly and program custom request/response editors.

For instance, here is an script that adds a response
header with the number of seen responses:

```python
from pyscalpel import Response

count = 0

def response(res: Response) -> Response:
    global count

    count += 1
    res.headers["count"] = count
    return res
```

## Available Hooks

The following list all available event hooks.

{{< readfile file="/generated/api/events.html" >}}

## Notes

-   If your hooks return `None`, they will follow these behaviors:

    -   `request()` or `response()`: The original request is be forwarded without any modifications.
    -   `req_edit_in()` or `res_edit_in()`: The editor tab is not displayed.
    -   `req_edit_out()` or `res_edit_out()`: The request is not modified.

-   If `req_edit_out()` or `res_edit_out()` isn't declared but `req_edit_in()` or `res_edit_in()` is, the corresponding editor will be **read-only**.

-   You do not have to declare every hook if you don't need them, if you only want to modify requests, you can declare the `request()` hook only.

## Further reading

Check out the [Custom Burp Editors]({{< relref "feature-editors" >}}) section.
