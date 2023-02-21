# Example request callback script
def request(req, text, tab_name, logger):
    logger.logToOutput(f"tab_name: {tab_name},\n\nreq: {req},\n\ntext: {text}\n\n-------------")
