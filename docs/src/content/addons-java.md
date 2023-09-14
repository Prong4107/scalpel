---
title: "Accessing the Burp API"
menu: "addons"
menu:
    addons:
        weight: 2
---

# Accessing the Burp API

Scalpel communicates with Burp through its Java API and provides the user with an execution context in which they should only have to use **Python objects**.

However, Scalpel's Python library focuses on handling HTTP objects and **does not provide utilities for all the Burp API features** (for example, the ability to generate Collaborator payloads).


For such cases, the user can directly access the [MontoyaApi Java object](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html) to find out what objects can be used.


## Examples

    _A script spoofing the Host header with a collaborator payload:_

        ```python
        from pyscalpel import Request, ctx

        # Spoof the Host header to a Burp collaborator payload to detect out-of-band interactions and HTTP SSRFs

        # Directly access the Montoya API Java object to generate a payload
        PAYLOAD = str(ctx["API"].collaborator().defaultPayloadGenerator().generatePayload())


        def request(req: Request) -> Request | None:
            req.host_header = PAYLOAD
            return req
        ```

        > 💡 [PortSwigger's documentation for the Collaborator Generator](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/collaborator/CollaboratorPayloadGenerator.html#generatePayload(burp.api.montoya.collaborator.PayloadOption...)).

<br>

_A script sending every request that has the `cmd` param to Burp Repeater:_

        ```python
        from pyscalpel import Request, ctx
        from threading import Thread

        # Send every request that contains the "cmd" param to repeater

        # Ensure added request are unique by using a set
        seen = set()


        def request(req: Request) -> None:
            cmd = req.query.get("cmd")
            if cmd is not None and cmd not in seen:
                # Convert request to Burp format
                breq = req.to_burp()

                # Directly access the Montoya API Java object to send the request to repeater
                repeater = ctx["API"].repeater()

                # Wait for sendToRepeater() while intercepting a request causes a Burp deadlock
                Thread(target=lambda: repeater.sendToRepeater(breq, f"cmd={cmd}")).start()
        ```

        > 💡 [PortSwigger's documentation for Burp repeater](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/repeater/Repeater.html)
