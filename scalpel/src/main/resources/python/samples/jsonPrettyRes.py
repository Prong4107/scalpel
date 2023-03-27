import json
from pyscalpel.utils import IHttpResponse, logger, get_bytes, to_bytes


# Test script that prettyfies JSON response and adds debug headers


def res_edit_in_Scalpel(res: IHttpResponse) -> bytes | None:
    try:
        logger.logToOutput("Python: res_edit_in_...() called")
        body: bytes = get_bytes(res.body())
        obj = json.loads(body)
        pretty = json.dumps(obj, indent=2)
        return to_bytes(res.withBody(pretty))
    except Exception as e:
        logger.logToError(f"py res_in errr: \n\t{e}")
