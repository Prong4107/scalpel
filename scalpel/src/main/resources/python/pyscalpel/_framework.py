import traceback
from sys import _getframe
import inspect
from typing import Callable, TypeVar, cast, List
import sys

__logger__.logToOutput("Python: Loading _framework.py ...")

try:
    # TODO: Dont hardcode this
    PATH_TO_ADD = "/home/nol/Desktop/piperpp/scalpel/scalpel/src/main/resources/python"

    # add to path
    if PATH_TO_ADD not in sys.path:
        sys.path.append(PATH_TO_ADD)

    import pyscalpel._globals

    pyscalpel._globals.logger = __logger__  # type: ignore

    user_script = "/home/nol/Desktop/piperpp/scalpel/scalpel/src/main/resources/python/samples/editorTest.py"

    import importlib.util

    # specify the absolute path of the script you want to import
    path = user_script

    # create a module spec based on the script path
    spec = importlib.util.spec_from_file_location("module_name", path)

    assert spec is not None
    assert spec.loader is not None

    # create a module based on the spec
    user_module = importlib.util.module_from_spec(spec)

    # load the user_module into memory
    spec.loader.exec_module(user_module)

    from pyscalpel.utils import IHttpRequest, logger, IHttpResponse, update_header, to_bytes, new_request, new_response

    CallbackReturn = TypeVar(
        'CallbackReturn', IHttpRequest, IHttpResponse, bytes) | None

    CallbackType = Callable[..., CallbackReturn]

    callable_objs: dict[str, CallbackType] = dict([(name, obj) for name, obj in inspect.getmembers(
        user_module) if callable(obj)])

    def fun_name(frame=1):
        return _getframe(frame).f_code.co_name

    def _try_wrap(callback: CallbackType) -> CallbackType:
        logger.logToOutput(f"Python: _try_wrap() called")

        def new_cb(*args, **kwargs):
            try:
                logger.logToOutput(f"Python: _try_wrap_cb() called")
                return callback(*args, **kwargs)
            except Exception as ex:
                logger.logToError(f"Python: {fun_name(2)}() error:\n\t{ex}")
                logger.logToError(traceback.format_exc())
        return new_cb

    def _if_present(callback: Callable[..., CallbackReturn | None]) -> Callable[..., CallbackReturn | None]:
        logger.logToOutput(f"Python: _if_present({callback.__name__}) called")

        def new_cb(*args, **kwargs) -> CallbackReturn | None:
            logger.logToOutput(
                f"Python: _if_present_cb({callback.__name__}) called")
            name = callback.__name__.removeprefix("_")
            user_cb = callable_objs.get(name)
            if (user_cb is not None):
                logger.logToOutput(f"Python: {name}() is present")
                return _try_wrap(callback)(*args, **dict(kwargs, callback=user_cb))
            logger.logToOutput(f"Python: {name}() is not present")

        return new_cb

    @_if_present
    def _request(req: IHttpRequest, callback: CallbackType = ...) -> IHttpRequest | None:
        return cast(IHttpRequest | None, callback(req))

    @_if_present
    def _response(res: IHttpResponse, callback: CallbackType = ...) -> IHttpResponse | None:
        return cast(IHttpResponse | None, callback(res))

    @_if_present
    def _req_edit_in(req: IHttpRequest, callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(req))

    @_if_present
    def _req_edit_out(req: IHttpRequest, text: List[int], callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(req, bytes(text)))

    @_if_present
    def _res_edit_in(res: IHttpResponse, callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(res))

    @_if_present
    def _res_edit_out(res: IHttpResponse, text: List[int], callback: CallbackType = ...) -> bytes | None:
        return cast(bytes | None, callback(res, bytes(text)))

    logger.logToOutput("Python: Loaded _framework.py")

except Exception as global_ex:
    __logger__.logToOutput("Python: Failed loading _framework.py")
    __logger__.logToError("Couldn't load script:")
    __logger__.logToError(global_ex)
    __logger__.logToError(traceback.format_exc())
