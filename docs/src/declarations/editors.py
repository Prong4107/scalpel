"""
    Scalpel allows choosing between normal and binary editors, to do so,
    the user can apply the `editor` decorator to the `req_edit_in` / `res_edit_int` hook:
"""
from pyscalpel.edit import editor


__all__ = ["editor"]
