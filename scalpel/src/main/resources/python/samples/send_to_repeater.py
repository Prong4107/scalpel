from pyscalpel.http import Request
from pyscalpel.burp_utils import ctx
from threading import Thread

# Send every request that contains the "cmd" param to repeater

# Set to ensure added requests are unique
seen = set()


def request(req: Request) -> None:
    cmd = req.query.get("cmd")
    if cmd is not None and cmd not in seen:
        seen.add(cmd)

        # Convert request to Burp format.
        breq = req.to_burp()

        # Directly access the Montoya API Java object to send the request to repeater
        repeater = ctx["API"].repeater()

        # waiting for sendToRepeater while intercepting a request causes a Burp deadlock
        Thread(target=lambda: repeater.sendToRepeater(breq, f"cmd={cmd}")).start()
