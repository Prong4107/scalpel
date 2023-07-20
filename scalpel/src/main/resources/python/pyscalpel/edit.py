from typing import Callable, Literal

EditorMode = Literal["raw", "hex"]


def editor(mode: EditorMode):
    """Decorator to specify the editor type for a given hook

    Args:
        mode (EDITOR_MODE): The editor mode (raw, hex,...)
    """

    def decorator(hook: Callable):
        hook.__annotations__["scalpel_editor_mode"] = mode
        return hook

    return decorator
