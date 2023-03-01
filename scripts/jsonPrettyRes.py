from burp.api.montoya.http.message.requests import HttpRequest
import json

# Test script that prettyfies JSON response and adds debug headers

def request(req, text, tab_name, logger):
    try:
        logger.logToOutput("Python: request() called")
        return HttpRequest.httpRequest(text).withService(req.httpService()).withAddedHeader("X-Python-Intercept-Request", "Hello")
    except:
        logger.logToError("Python: error")
    return req


def response(res, logger):
    logger.logToOutput("Python: response() called")
    return res.withAddedHeader("X-Python-Intercept-Response", "true")

# OK
def req_edit_in_Scalpel(req, logger):
    try:
        logger.logToOutput("Python: req_edit_in_...() called")
        logger.logToOutput(f"{req.getClass()}")
        return req.withAddedHeader("X-Python-In-Request-Editor", "true").toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"py req_in err: \n\t{e}")

# OK
def req_edit_out_Scalpel(reqBytes: bytes, logger):
    logger.logToOutput("Python: req_edit_out_...() called")
    logger.logToOutput(f"Python: {reqBytes}")
    return reqBytes

def res_edit_in_Scalpel(res, logger):
    try:
        logger.logToOutput("Python: res_edit_in_...() called")
        body: bytes = bytes(res.body().getBytes())
        obj = json.loads(body)
        pretty =  json.dumps(obj, indent=2)
        return res.withAddedHeader("X-Python-In-Response-Editor", "true").withBody(pretty).toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"py res_in errr: \n\t{e}")

# KO
# def req_edit_Scalpel(req, logger):s
#     logger.logToOutput("called")
#     return 0xDEADBEEF
