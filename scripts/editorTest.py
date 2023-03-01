from burp.api.montoya.http.message.requests import HttpRequest
from burp.api.montoya.core import ByteArray


# Test script that adds debug headers

def request(req, logger=None):
    try:
        logger.logToOutput("Python: request() called")
        return req.withUpdatedHeader("X-Python-Intercept-Request", "Hello")
    except:
        logger.logToError("Python: error")
    return req


def response(res, logger=None):
    logger.logToOutput("Python: response() called")
    return res.withUpdatedHeader("X-Python-Intercept-Response", "true")

# OK
def req_edit_in_Scalpel(req, logger=None):
    try:
        logger.logToOutput("Python: req_edit_in_...() called")
        logger.logToOutput(f"{req.getClass()}")
        return req.withUpdatedHeader("X-Python-In-Request-Editor", "true").toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"py req_in err: \n\t{e}")

# OK
def req_edit_out_Scalpel(req, text: bytes, logger=None):
    logger.logToOutput("Python: req_edit_out_...() called")
    logger.logToOutput(f"Python: {text}")
    return HttpRequest.httpRequest(ByteArray.byteArray(text)).withUpdatedHeader("X-Python-Out-Request-Editor", "true").toByteArray().getBytes()

def res_edit_in_Scalpel(res, logger=None):
    try:
        logger.logToOutput("Python: res_edit_in_...() called")
        return res.withUpdatedHeader("X-Python-In-Response-Editor", "true").toByteArray().getBytes()
    except Exception as e:
        logger.logToError(f"py res_in errr: \n\t{e}")

# KO
# def req_edit_Scalpel(req, logger):s
#     logger.logToOutput("called")
#     return 0xDEADBEEF
