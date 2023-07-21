from typing import Callable, Literal

EditorMode = Literal["raw", "hex", "octal", "binary", "decimal"]


def editor(mode: EditorMode):
    """Decorator to specify the editor type for a given hook

    This can be applied to a req_edit_in / res_edit_in hook declaration to specify the editor that should be displayed in Burp

    Example:
    ```py
        @editor("hex")
        def req_edit_in(req: Request) -> bytes | None:
            return bytes(req)
    ```
    This displays the request in an hex editor.

    Currently, the only modes supported are `"raw"`, `"hex"`, `"octal"`, `"binary"` and `"decimal"`.


    Args:
        mode (EDITOR_MODE): The editor mode (raw, hex,...)
    """

    def decorator(hook: Callable):
        hook.__annotations__["scalpel_editor_mode"] = mode
        return hook

    return decorator
