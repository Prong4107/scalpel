from pyscalpel.http import Request
from pyscalpel.burp_utils import ctx

# Spoof the Host header to a Burp collaborator payload to detect out-of-band interactions and HTTP SSRFs

# Directly access the Montoya API Java object to generate a payload
PAYLOAD = str(ctx["API"].collaborator().defaultPayloadGenerator().generatePayload())


def request(req: Request) -> Request | None:
    req.host_header = PAYLOAD
    return req
