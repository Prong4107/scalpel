---
title: "Event Hooks & API"
url: "api/events.html"
aliases:
    - /addons-events/
layout: single
menu:
    addons:
        weight: 3
---

# Event Hooks

Scalpel scripts hook into Burps's internal mechanisms through event hooks. These are
implemented as methods with a set of well-known names. Events
receive `Request`, `Response`, `Flow` and `bytes` objects as arguments - by modifying these objects, scripts can
change traffic on the fly and program custom request/response editors. For instance, here is an script that adds a response
header with a count of the number of responses seen:

```python
from pyscalpel.http import Response

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

