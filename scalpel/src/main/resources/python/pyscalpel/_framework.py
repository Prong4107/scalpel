import traceback
from sys import _getframe
import inspect
from typing import Callable, TypeVar, cast, List
import sys


# Debug logger to use if for some reason the logger is not initialized
class DebugLogger:
    def logToOutput(self, msg: str):
        print(msg)

    def logToError(self, msg: str):
        print(msg, file=sys.stderr)


# Try to use the logger a first time to ensure it is initialized
try:
    __logger__.logToOutput("Python: Loading _framework.py ...")
except NameError:
    # Initialize the logger
    __logger__ = DebugLogger()
    __logger__.logToOutput(
        "Python: Initializing logger ...\nWARN: Logger not initialized, using DebugLogger")

try:
    # Import the globals module to set the logger
    import pyscalpel._globals

    # Set the logger in the globals module
    pyscalpel._globals.logger = __logger__

    # Get the user script path from the JEP initialized variable
    user_script = __user_script__

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

    # Declare convenient types for the callbacks
    from pyscalpel.utils import IHttpRequest, logger, IHttpResponse

    CallbackReturn = TypeVar(
        'CallbackReturn', IHttpRequest, IHttpResponse, bytes) | None

    CallbackType = Callable[..., CallbackReturn]

    # Get all the callable objects from the user module
    callable_objs = {name: obj for name,
                     obj in inspect.getmembers(user_module) if callable(obj)}

    # Utility function to get the name of the caller function
    def fun_name(frame=1):
        return _getframe(frame).f_code.co_name

    # Utility function to wrap a callback in a try catch block and add some debug logs.
    def _try_wrap(callback: CallbackType) -> CallbackType:
        logger.logToOutput("Python: _try_wrap() called")

        def new_cb(*args, **kwargs):
            try:
                logger.logToOutput("Python: _try_wrap_cb() called")
                return callback(*args, **kwargs)
            except Exception as ex:
                logger.logToError(f"Python: {fun_name(2)}() error:\n\t{ex}")
                logger.logToError(traceback.format_exc())
        return new_cb

    # Decorator to return a  None lambda when the callback is not present in the user script.
    def _try_if_present(callback: Callable[..., CallbackReturn | None]) -> Callable[..., CallbackReturn | None]:
        logger.logToOutput(
            f"Python: _try_if_present({callback.__name__}) called")

        # Remove the leading underscore from the callback name
        name = callback.__name__.removeprefix("_")

        # Get the user callback from the user script's callable objects
        user_cb = callable_objs.get(name)

        # Ensure the user callback is present
        if (user_cb is not None):
            logger.logToOutput(f"Python: {name}() is present")

            # Wrap the user callback in a try catch block and return it
            @_try_wrap
            def new_cb(*args, **kwargs) -> CallbackReturn | None:
                return callback(*args, **dict(kwargs, callback=user_cb))

            # Return the wrapped callback
            return new_cb

        logger.logToOutput(f"Python: {name}() is not present")
        # Ignore the callback.
        return lambda: None

    @_try_if_present
    def _request(req: IHttpRequest, callback: CallbackType = ...) -> IHttpRequest | None:
        return cast(IHttpRequest | None, callback(req))

    @_try_if_present
    def _response(res: IHttpResponse, callback: CallbackType = ...) -> IHttpResponse | None:
        return cast(IHttpResponse | None, callback(res))

    @_try_if_present
    def _req_edit_in(req: IHttpRequest, callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(req))

    @_try_if_present
    def _req_edit_out(req: IHttpRequest, text: List[int], callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(req, bytes(text)))

    @_try_if_present
    def _res_edit_in(res: IHttpResponse, callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(res))

    @_try_if_present
    def _res_edit_out(res: IHttpResponse, text: List[int], callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(res, bytes(text)))

    logger.logToOutput("Python: Loaded _framework.py")

except Exception as global_ex:
    # Global generic exception handler to ensure the error is logged and visible to the user.
    __logger__.logToOutput("Python: Failed loading _framework.py")
    __logger__.logToError("Couldn't load script:")
    __logger__.logToError(global_ex)
    __logger__.logToError(traceback.format_exc())
