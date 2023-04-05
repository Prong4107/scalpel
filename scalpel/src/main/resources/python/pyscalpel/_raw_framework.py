import inspect
import sys
import traceback
from functools import wraps
from sys import _getframe
from typing import Callable, TypeVar, cast


class DebugLogger:
    """Debug logger to use if for some reason the logger is not initialized"""

    def logToOutput(self, msg: str):  # pylint: disable=invalid-name
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def logToError(self, msg: str):  # pylint: disable=invalid-name
        """Prints the message to the standard error

        Args:
            msg (str): The message to print
        """
        print(msg, file=sys.stderr)


logger = DebugLogger()

# Try to use the logger a first time to ensure it is initialized
try:
    logger = __logger__  # type: ignore
    logger.logToOutput("Python: Loading _framework.py ...")
except NameError:
    logger.logToOutput("Python: Initializing logger ...\nWARNING: Logger not initialized, using DebugLogger")

try:
    # Import the globals module to set the logger
    import pyscalpel._globals

    # Set the logger in the globals module
    pyscalpel._globals.logger = logger  # pylint: disable=protected-access

    # Get the user script path from the JEP initialized variable
    user_script: str = __user_script__  # type: ignore # pylint: disable=undefined-variable

    # Get utils to dynamically import the user script in a convinient way
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

    # Declare convenient types for the callbacks
    CallbackReturn = TypeVar("CallbackReturn", IHttpRequest, IHttpResponse, bytes) | None

    CallbackType = Callable[..., CallbackReturn]

    # Get all the callable objects from the user module
    callable_objs = {name: obj for name, obj in inspect.getmembers(user_module) if callable(obj)}

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
        logger.logToOutput("Python: _try_wrap() called")

        @wraps(callback)
        def _wrapped_cb(*args, **kwargs):
            try:
                logger.logToOutput("Python: _wrapped_cb() called")
                return callback(*args, **kwargs)
            except Exception as ex:  # pylint: disable=broad-except
                logger.logToError(f"Python: {fun_name(1)}() error:\n\t{ex}")
                logger.logToError(traceback.format_exc())

        return _wrapped_cb

    def _try_if_present(callback: Callable[..., CallbackReturn]) -> Callable[..., CallbackReturn]:
        """Decorator to return a  None lambda when the callback is not present in the user script.

        Args:
            callback (Callable[..., CallbackReturn]): The callback to wrap

        Returns:
            Callable[..., CallbackReturn]: The wrapped callback
        """
        logger.logToOutput(f"Python: _try_if_present({callback.__name__}) called")

        # Remove the leading underscore from the callback name
        name = callback.__name__.removeprefix("_")

        # Get the user callback from the user script's callable objects
        user_cb = callable_objs.get(name)

        # Ensure the user callback is present
        if user_cb is not None:
            logger.logToOutput(f"Python: {name}() is present")

            # Wrap the user callback in a try catch block and return it
            @_try_wrap
            @wraps(user_cb)
            def new_cb(*args, **kwargs) -> CallbackReturn:
                return callback(*args, **kwargs, callback=user_cb)

            # Return the wrapped callback
            return new_cb

        logger.logToOutput(f"Python: {name}() is not present")
        # Ignore the callback.
        return lambda *_, **__: None

    @_try_if_present
    def _request(req: IHttpRequest, callback: CallbackType = ...) -> IHttpRequest | None:
        """Wrapper for the request callback

        Args:
            req (IHttpRequest): The request object
            callback (CallbackType, optional): The user callback.

        Returns:
            IHttpRequest | None: The modified request object or None for an unmodified request
        """
        return cast(IHttpRequest | None, callback(req))

    @_try_if_present
    def _response(res: IHttpResponse, callback: CallbackType = ...) -> IHttpResponse | None:
        """Wrapper for the response callback

        Args:
            res (IHttpResponse): The response object
            callback (CallbackType, optional): The user callback.

        Returns:
            IHttpResponse | None: The modified response object or None for an unmodified response
        """
        return cast(IHttpResponse | None, callback(res))

    @_try_if_present
    def _req_edit_in(req: IHttpRequest, callback: CallbackType = ...) -> bytes | None:
        """Wrapper for the request edit callback

        Args:
            req (IHttpRequest): The request object
            callback (CallbackType, optional): The user callback. Defaults to None.

        Returns:
            bytes | None: The bytes to display in the editor or None for a disabled editor
        """
        return cast(bytes | None, callback(req))

    @_try_if_present
    def _req_edit_out(req: IHttpRequest, text: list[int], callback: CallbackType = ...) -> bytes | None:
        """Wrapper for the request edit callback

        Args:
            req (IHttpRequest): The request object
            text (list[int]): The editor content
            callback (CallbackType, optional): The user callback.

        Returns:
            bytes | None: The bytes to construct the new request from
                or None for an unmodified request
        """

        return cast(bytes | None, callback(req, bytes(text)))

    @_try_if_present
    def _res_edit_in(res: IHttpResponse, callback: CallbackType = ...) -> bytes | None:
        """Wrapper for the response edit callback

        Args:
            res (IHttpResponse): The response object
            callback (CallbackType, optional): The user callback.

        Returns:
            bytes | None: The bytes to display in the editor or None for a disabled editor
        """
        return cast(bytes | None, callback(res))

    @_try_if_present
    def _res_edit_out(res: IHttpResponse, text: list[int], callback: CallbackType = ...) -> bytes | None:
        """Wrapper for the response edit callback

        Args:
            res (IHttpResponse): The response object
            text (list[int]): The editor content
            callback (CallbackType, optional): The user callback.

        Returns:
            bytes | None: The bytes to construct the new response from
                or None for an unmodified response
        """
        return cast(bytes | None, callback(res, bytes(text)))

    logger.logToOutput("Python: Loaded _framework.py")

except Exception as global_ex:  # pylint: disable=broad-except
    # Global generic exception handler to ensure the error is logged and visible to the user.
    logger.logToOutput("Python: Failed loading _framework.py")
    logger.logToError("Couldn't load script:")
    logger.logToError(str(global_ex))
    logger.logToError(traceback.format_exc())
