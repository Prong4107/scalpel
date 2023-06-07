import fnmatch


def host_is(host: str, *patterns: str) -> bool:
    """Matches a host using unix-like wildcard matching against multiple patterns

    Args:
        host (str): The host to match against
        patterns (str): The patterns to use

    Returns:
        bool: The match result (True if matching, else False)
    """
    for pattern in patterns:
        if fnmatch.fnmatch(host, pattern):
            return True
    return False
