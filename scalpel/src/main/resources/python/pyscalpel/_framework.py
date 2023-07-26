import traceback
from sys import _getframe
import inspect
from typing import Callable, TypeVar, cast, Any, TypedDict
import sys
from functools import wraps


# Define a debug logger to be able to debug cases where the logger is not initialized
#   or the path isn't well set
#   or the _framework is invoked by pdoc or other tools
#
# Output will be printed to the terminal
class DebugLogger:
    """
    Debug logger to use if for some reason the logger is not initialized
    or pyscalpel.logger cannot be imported.
    """

    def all(self, msg: str):
        print(msg)

    def trace(self, msg: str):
        print(msg)

    def debug(self, msg: str):
        print(msg)

    def info(self, msg: str):
        print(msg)

    def warn(self, msg: str):
        print(msg)

    def fatal(self, msg: str):
        print(msg)

    def error(self, msg: str):
        print(msg, file=sys.stderr)


logger = DebugLogger()

VENV = None

try:
    from pyscalpel.venv import activate
    from pyscalpel.java.scalpel_types import Context

    ctx: Context = cast(Context, __scalpel__)  # type: ignore pylint: disable=undefined-variable

    logger = ctx["logger"]  # type: ignore
    logger.all("Python: Loading _framework.py ...")

    VENV = ctx["venv"]

    activate(VENV)

    # import debugpy

    # debugpy.listen(("localhost", 5678))

    # Import the globals module to set the logger
    import pyscalpel._globals

    # Set the logger in the globals module
    pyscalpel._globals.logger = logger  # pylint: disable=protected-access
    pyscalpel._globals.ctx = ctx  # pylint: disable=protected-access

    # Get the user script path from the JEP initialized variable
    user_script: str = ctx["user_script"]

    # Get utils to dynamically import the user script in a convenient way
    import importlib.util

    # specify the absolute path of the script you want to import
    path = user_script

    # create a module spec based on the script path
    spec = importlib.util.spec_from_file_location("scalpel_user_module", path)

    # Assert that the provided path can be loaded
    assert spec is not None
    assert spec.loader is not None

    # create a module based on the spec
    user_module = importlib.util.module_from_spec(spec)

    # load the user_module into memory
    spec.loader.exec_module(user_module)

    from pyscalpel.burp_utils import IHttpRequest, IHttpResponse
    from pyscalpel.java.burp.http_service import IHttpService
    from pyscalpel.http import Request, Response, Flow
    from pyscalpel.events import Events

    # Declare convenient types for the callbacks
    CallbackReturn = TypeVar("CallbackReturn", Request, Response, bytes) | None

    CallbackType = Callable[..., CallbackReturn]

    # Get all the callable objects from the user module
    callable_objs: dict[str, Callable] = {
        name: obj for name, obj in inspect.getmembers(user_module) if callable(obj)
    }

    match_callback: Callable[[Flow, Events], bool] = callable_objs.get("match") or (
        lambda _, __: True
    )

    class CallableData(TypedDict):
        name: str
        annotations: dict[str, Any]

    def _get_callables() -> list[CallableData]:
        logger.debug("Python: _get_callables() called")
        # Also return the annotations because they contain the editor mode (hex,raw)
        # Annotations are a dict so they will be converted to HashMap
        # https://github.com/ninia/jep/wiki/How-Jep-Works#objects:~:text=Dict%20%2D%3E%20java.util.HashMap
        return [
            {"name": name, "annotations": hook.__annotations__}
            for name, hook in callable_objs.items()
        ]

    def call_match_callback(*args) -> bool:
        """Calls the match callback with the correct parameters.

        Returns:
            bool: The match callback result
        """
        params_len = len(inspect.signature(match_callback).parameters)
        filtered_args = args[:params_len]
        return match_callback(*filtered_args)

    def fun_name(frame=1):
        """Returns the name of the caller function

        Args:
            frame (int, optional): The frame to get the name from. Defaults to 1.
        """
        return _getframe(frame).f_code.co_name

    def _try_wrap(callback: CallbackType) -> CallbackType:
        """Wraps a callback in a try catch block and add some debug logs.

        Args:
            callback (CallbackType): The callback to wrap

        Returns:
            CallbackType: The wrapped callback
        """
        logger.debug("Python: _try_wrap() called")

        # Define the wrapper function
        @wraps(callback)
        def _wrapped_cb(*args, **kwargs):
            try:
                logger.debug(f"Python: _wrapped_cb() for {callback.__name__} called")
                return callback(*args, **kwargs)
            except Exception as ex:  # pylint: disable=broad-except
                logger.error(f"Python: {callback.__name__}() error:\n\t{ex}")
                logger.error(traceback.format_exc())

        # Replace the callback with the wrapped one
        return _wrapped_cb

    def _try_if_present(
        callback: Callable[..., CallbackReturn]
    ) -> Callable[..., CallbackReturn]:
        """Decorator to return a  None lambda when the callback is not present in the user script.

        Args:
            callback (Callable[..., CallbackReturn]): The callback to wrap

        Returns:
            Callable[..., CallbackReturn]: The wrapped callback
        """
        logger.debug(f"Python: _try_if_present({callback.__name__}) called")

        # Remove the leading underscore from the callback name
        name = callback.__name__.removeprefix("_")

        # Get the user callback from the user script's callable objects
        user_cb = callable_objs.get(name)

        # Ensure the user callback is present
        if user_cb is not None:
            logger.debug(f"Python: {name}() is present")

            # Wrap the user callback in a try catch block and return it
            @_try_wrap
            @wraps(user_cb)
            def new_cb(*args, **kwargs) -> CallbackReturn:
                return callback(*args, **kwargs, callback=user_cb)

            # Return the wrapped callback
            return new_cb

        logger.debug(f"Python: {name}() is not present")

        # Ignore the callback.
        return lambda *_, **__: None

    # TODO: HttpService param is useless in this request
    @_try_if_present
    def _request(
        req: IHttpRequest, service: IHttpService, callback: CallbackType = ...
    ) -> IHttpRequest | None:
        """Wrapper for the request callback

        Args:
            req (IHttpRequest): The request object
            callback (CallbackType, optional): The user callback.

        Returns:
            IHttpRequest | None: The modified request object or None for an unmodified request
        """
        py_req = Request.from_burp(req, service)

        flow = Flow(py_req.scheme, py_req.host, py_req.port, py_req)
        if not call_match_callback(flow, "response"):
            return None

        # Call the user callback
        processed_req = cast(Request | None, callback(py_req))

        # Convert the request to a Burp request
        return processed_req.to_burp() if processed_req is not None else None

    @_try_if_present
    def _response(
        res: IHttpResponse, service: IHttpService, callback: CallbackType = ...
    ) -> IHttpResponse | None:
        """Wrapper for the response callback

        Args:
            res (IHttpResponse): The response object
            callback (CallbackType, optional): The user callback.

        Returns:
            IHttpResponse | None: The modified response object or None for an unmodified response
        """
        py_res = Response.from_burp(res, service)

        flow = Flow(py_res.scheme, py_res.host, py_res.port, py_res.request, py_res)
        if not call_match_callback(flow, "response"):
            return None

        result_res = cast(Response | None, callback(py_res))

        return result_res.to_burp() if result_res is not None else None

    # TODO: update docstrings
    @_try_wrap
    def _req_edit_in(
        req: IHttpRequest, service: IHttpService, callback_suffix: str = ...
    ) -> bytes | None:
        """Wrapper for the request edit callback

        Args:
            req (IHttpRequest): The request object

        Returns:
            bytes | None: The bytes to display in the editor or None for a disabled editor
        """
        logger.debug(f"Python: _req_edit_in -> {callback_suffix}")
        callback = callable_objs.get("req_edit_in" + callback_suffix)
        if callback is None:
            return

        py_req = Request.from_burp(req, service)

        flow = Flow(py_req.scheme, py_req.host, py_req.port, py_req)
        if not call_match_callback(flow, "req_edit_in"):
            return None

        logger.debug(f"Python: calling {callback.__name__}")
        # Call the user callback and return the bytes to display in the editor
        return cast(bytes | None, callback(py_req))

    @_try_wrap
    def _req_edit_out(
        req: IHttpRequest,
        service: IHttpService,
        text: list[int],
        callback_suffix: str = ...,
    ) -> IHttpRequest | None:
        """Wrapper for the request edit callback

        Args:
            req (IHttpRequest): The request object
            text (list[int]): The editor content
            callback (CallbackType, optional): The user callback.

        Returns:
            bytes | None: The bytes to construct the new request from
                or None for an unmodified request
        """
        logger.debug(f"Python: _req_edit_out -> {callback_suffix}")
        callback = callable_objs.get("req_edit_out" + callback_suffix)
        if callback is None:
            return

        py_req = Request.from_burp(req, service)

        flow = Flow(py_req.scheme, py_req.host, py_req.port, py_req, text=bytes(text))
        if not call_match_callback(flow, "req_edit_out"):
            return None

        logger.debug(f"Python: calling {callback.__name__}")
        # Call the user callback and return the bytes to construct the new request from
        result = cast(Request | None, callback(py_req, bytes(text)))
        return result and result.to_burp()

    @_try_wrap
    def _res_edit_in(
        res: IHttpResponse,
        request: IHttpRequest,
        service: IHttpService,
        callback_suffix: str = ...,
    ) -> bytes | None:
        """Wrapper for the response edit callback

        Args:
            res (IHttpResponse): The response object
            callback (CallbackType, optional): The user callback.

        Returns:
            bytes | None: The bytes to display in the editor or None for a disabled editor
        """
        logger.debug(f"Python: _res_edit_in -> {callback_suffix}")
        callback = callable_objs.get("res_edit_in" + callback_suffix)
        if callback is None:
            return

        py_res = Response.from_burp(res, service=service, request=request)

        flow = Flow(py_res.scheme, py_res.host, py_res.port, py_res.request, py_res)
        if not call_match_callback(flow, "res_edit_in"):
            return None

        logger.debug(f"Python: calling {callback.__name__}")
        # Call the user callback and return the bytes to display in the editor
        return cast(bytes | None, callback(py_res))

    @_try_wrap
    def _res_edit_out(
        res: IHttpResponse,
        req: IHttpRequest,
        service: IHttpService,
        text: list[int],
        callback_suffix: str = ...,
    ) -> IHttpResponse | None:
        """Wrapper for the response edit callback

        Args:
            res (IHttpResponse): The response object
            text (list[int]): The editor content
            callback (CallbackType, optional): The user callback.

        Returns:
            bytes | None: The bytes to construct the new response from
                or None for an unmodified response
        """
        logger.debug(f"Python: _res_edit_out -> {callback_suffix}")
        callback = callable_objs.get("res_edit_out" + callback_suffix)
        if callback is None:
            return

        py_res = Response.from_burp(res, service=service, request=req)

        flow = Flow(
            py_res.scheme, py_res.host, py_res.port, py_res.request, py_res, bytes(text)
        )
        if not call_match_callback(flow, "res_edit_out"):
            return None

        logger.debug(f"Python: calling {callback.__name__}")
        # Call the user callback and return the bytes to construct the new response from
        result = cast(Response | None, callback(py_res, bytes(text)))
        return result and result.to_burp()

    logger.all("Python: Loaded _framework.py")

except Exception as global_ex:  # pylint: disable=broad-except
    # Global generic exception handler to ensure the error is logged and visible to the user.
    logger.fatal("Couldn't load script:")
    logger.fatal(str(global_ex))
    logger.fatal(traceback.format_exc())
    logger.all("Python: Failed loading _framework.py")
