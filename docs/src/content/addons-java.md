---
title: "Accessing the Burp API"
menu: "addons"
menu:
    addons:
        weight: 2
---

# Accessing the Burp API

Scalpel communicates with Burp through it's Java API and
provides the user with an execution context where they should only have to use Python objects.

However, Scalpel's Python library focuses on handling HTTP objects
and does not provide utilities for all the Burp API features.

For example, Scalpel library itself does not provide the ability to generate Collaborator payloads.

For cases like these, the user can directly access the [MontoyaApi Java object](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html)

Users may look at the [Montoya API Javadoc](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html) to known which objects they can access.

-   Examples:

    -   A script spoofing the Host header with a collaborator payload

        ```python
        from pyscalpel.http import Request
        from pyscalpel.burp_utils import ctx

        # Spoof the Host header to a Burp collaborator payload to detect out-of-band interactions and HTTP SSRFs

        # Directly access the Montoya API Java object to generate a payload
        PAYLOAD = str(ctx["API"].collaborator().defaultPayloadGenerator().generatePayload())


        def request(req: Request) -> Request | None:
            req.host_header = PAYLOAD
            return req
        ```

        > Documentation for the collaborator generator can be found in the javadoc at:
        >
        > https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/collaborator/CollaboratorPayloadGenerator.html#generatePayload(burp.api.montoya.collaborator.PayloadOption...)

    -   A script sending every request having the "cmd" param to Burp Repeater:

        ```python
        from pyscalpel.http import Request
        from pyscalpel.burp_utils import ctx
        from threading import Thread

        # Send every request that contains the "cmd" param to repeater

        # Set to ensure added request are unique
        seen = set()


        def request(req: Request) -> None:
            cmd = req.query.get("cmd")
            if cmd is not None and cmd not in seen:
                # Convert request to Burp format.
                breq = req.to_burp()

                # Directly access the Montoya API Java object to send the request to repeater
                repeater = ctx["API"].repeater()

                # waiting for sendToRepeater while intercepting a request causes a Burp deadlock
                Thread(target=lambda: repeater.sendToRepeater(breq, f"cmd={cmd}")).start()
        ```

        > Documentation for Burp repeater can be found at:
        >
        > https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/repeater/Repeater.html
