"""
This is a module providing tools to handle Burp HTTP traffic through the use of the Scalpel extension.

It provides many utilities to manipulate HTTP requests, responses and converting data.
"""

from pyscalpel.http import Request, Response, Flow
from pyscalpel.burp_utils import ctx

__all__ = ["Request", "Response", "Flow", "ctx"]
