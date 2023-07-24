from pyscalpel.http import Request, Response, Flow
from pyscalpel.events import Events


def match(flow: Flow, events: Events) -> bool:
    """- Determines if an event will be handled by a hook.

    - Args:
        - flow (Flow): The event context (contains request and potential response)
        - events (Events): The hook type (request, response, req_edit_in, ...)

    - Returns:
        - bool: Whether the event should be treated.
    """


def request(req: Request) -> Request | None:
    """- Intercepts a request and returns one to replace with.

    - Args:
        - req (Request): The intercepted request

    - Returns:
        - Request | None: The modified request, or `None` to ignore the request.
    """


def response(res: Response) -> Response | None:
    """- Intercepts a response and returns one to replace with.

    - Args:
        - res (Response): The intercepted response

    - Returns:
        - Response | None: The modified response, or `None` to ignore the response.
    """


def req_edit_in(req: Request) -> bytes | None:
    """- Create / update an editor from a request
       - May be used to decode a request to plain text.

    - Args:
        - req (Request): The HTTP request

    - Returns:
        - bytes | None: The editor contents
    """


def req_edit_out(req: Request, modified_content: bytes) -> Request | None:
    """- Updates a request from an editor's content
       - May be used to encode a request from plaintext (modified_content)

    - Args:
        - req (Request): The original request
        - modified_content (bytes): The editor's content

    - Returns:
        - Request | None: The new request.
    """


def res_edit_in(res: Response) -> bytes | None:
    """- Create / update an editor from a response
       - May be used to decode a response to plain text.

    - Args:
        - res (Response): The HTTP response

    - Returns:
        - bytes | None: The editor contents
    """


def res_edit_out(res: Response, modified_content: bytes) -> Response | None:
    """- Updates a request from an editor's content
       - May be used to encode a response from plaintext (modified_content)

    - Args:
        - res (Response): The original response
        - modified_content (bytes): The editor's content

    - Returns:
        - Response | None: The new response.
    """
