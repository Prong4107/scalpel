from burp.api.montoya.http.message.requests import HttpRequest

# Example request callback script
def request(req, text, tab_name, logger):
    try:
        logger.logToOutput("Python: request() called")
        return HttpRequest.httpRequest(text).withService(req.httpService()).withAddedHeader("X-Python-Request", "Hello")
    except:
        logger.logToError("Python: error")
    return req


def response(res, logger):
    logger.logToOutput("Python: response() called")
    return res.withAddedHeader("X-Python-Response", "Hello")

# OK
def req_edit_Scalpel(req, logger):
    logger.logToOutput("Python: req_edit_...() called")
    return req.withAddedHeader("X-Scalpel-Request-Editor", "true").toByteArray().getBytes()

# KO
# def req_edit_Scalpel(req, logger):s
#     logger.logToOutput("called")
#     return 0xDEADBEEF
