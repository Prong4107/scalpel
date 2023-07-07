import re
from urllib.parse import unquote, quote_plus
from typing import Mapping, Any, cast
from copy import deepcopy


# Map a list to an equivalent dictionary
# e.g: ["a","b","c"] -> {0:"a",1:"b",2:"c"}
def list_to_dict(lst: list):
    return {i: value for i, value in enumerate(lst)}


def merge_dict_in_list(source: dict, destination: list) -> list | dict:
    # Retain only integer keys:
    int_keys = sorted([key for key in source.keys() if isinstance(key, int)])
    array_values = [source[key] for key in int_keys]
    merged_array = array_values + destination

    if len(int_keys) == len(source.keys()):
        return merged_array

    return merge(source, list_to_dict(merged_array))


def merge(source: Any, destination: Any):
    source = deepcopy(source)
    destination = deepcopy(destination)

    match (source, destination):
        case (list(), list()):
            return source + destination
        case (dict(), list()):
            return merge_dict_in_list(source, destination)

    items = cast(Mapping, source).items()
    for key, value in items:
        if isinstance(value, dict) and isinstance(destination, dict):
            # get node or create one
            node = destination.setdefault(key, {})
            node = merge(value, node)
            destination[key] = node
        else:
            if (
                isinstance(value, list) or isinstance(value, tuple)
            ) and key in destination:
                value = merge(destination[key], value)
            if isinstance(key, str) and isinstance(destination, list):
                destination = list_to_dict(destination)
            destination[key] = value
    return destination


def qs_parse(qs: str, keep_blank_values: bool = True, strict_parsing: bool = False):
    tokens = {}
    pairs = [s2 for s1 in qs.split("&") for s2 in s1.split(";")]

    def get_name_value(name: str, value: str):
        name = unquote(name.replace("+", " "))
        value = unquote(value.replace("+", " "))
        matches = re.findall(r"([\s\w]+|\[\]|\[\w+\])", name)

        new_value: str | list | dict = value
        for i, match in enumerate(matches[::-1]):
            match match:
                case "[]":
                    if i == 0:
                        new_value = [new_value]
                    else:
                        # TODO: Ensure this does not break
                        new_value += new_value  # type: ignore
                case _ if re.match(r"\[\w+\]", match):
                    name = re.sub(r"[\[\]]", "", match)
                    new_value = {name: new_value}
                case _:
                    if match not in tokens:
                        match new_value:
                            case list() | tuple():
                                tokens[match] = []
                            case dict():
                                tokens[match] = {}

                    match new_value:
                        case _ if i == 0:
                            tokens[match] = new_value
                        case dict():
                            tokens[match] = merge(new_value, tokens[match])
                        case list() | tuple():
                            tokens[match] = tokens[match] + list(new_value)
                        case _:
                            if not isinstance(tokens[match], list):
                                # The key is duplicated, so we transform the first value into a list so we can append the new one
                                tokens[match] = [tokens[match]]
                            tokens[match].append(new_value)

    for name_val in pairs:
        if not name_val and not strict_parsing:
            continue
        nv = name_val.split("=")

        if len(nv) != 2:
            if strict_parsing:
                raise ValueError(f"Bad query field: {name_val}")
            # Handle case of a control-name with no equal sign
            if keep_blank_values:
                nv.append("")
            else:
                continue

        if len(nv[1]) or keep_blank_values:
            get_name_value(nv[0], nv[1])

    return tokens


def build_qs(query: Mapping) -> str:
    def dict_generator(indict, pre=None):
        pre = pre[:] if pre else []
        if isinstance(indict, dict):
            for key, value in indict.items():
                if isinstance(value, dict):
                    for d in dict_generator(value, pre + [key]):
                        yield d
                else:
                    yield pre + [key, value]
        else:
            yield indict

    paths = [i for i in dict_generator(query)]
    qs = []

    for path in paths:
        names = path[:-1]
        value = path[-1]
        s: list[str] = []
        for i, n in enumerate(names):
            n = f"[{n}]" if i > 0 else str(n)
            s.append(n)

        match value:
            case list() | tuple():
                for v in value:
                    multi = s[:]
                    if not s[-1].endswith("[]"):
                        multi.append("[]")
                    multi.append("=")
                    # URLEncode value
                    multi.append(quote_plus(str(v)))
                    qs.append("".join(multi))
            case _:
                s.append("=")
                # URLEncode value
                s.append(quote_plus(str(value)))
                qs.append("".join(s))

    return "&".join(qs)


def qs_parse_pairs(
    pairs: list[tuple[str, str]],
    keep_blank_values: bool = True,
    strict_parsing: bool = False,
) -> dict:
    tokens = {}

    def get_name_value(name: str, value: str):
        matches = re.findall(r"([\s\w]+|\[\]|\[\w+\])", name)

        new_value: str | list | dict = value
        for i, match in enumerate(matches[::-1]):
            match match:
                case "[]":
                    if i == 0:
                        new_value = [new_value]
                    else:
                        new_value += new_value  # type: ignore
                case _ if re.match(r"\[\w+\]", match):
                    name = re.sub(r"[\[\]]", "", match)
                    new_value = {name: new_value}
                case _:
                    if match not in tokens:
                        match new_value:
                            case list() | tuple():
                                tokens[match] = []
                            case dict():
                                tokens[match] = {}

                    match new_value:
                        case _ if i == 0:
                            tokens[match] = new_value
                        case dict():
                            tokens[match] = merge(new_value, tokens[match])
                        case list() | tuple():
                            tokens[match] = tokens[match] + list(new_value)
                        case _:
                            if not isinstance(tokens[match], list):
                                # The key is duplicated, so we transform the first value into a list so we can append the new one
                                tokens[match] = [tokens[match]]
                            tokens[match].append(new_value)

    for name_val in pairs:
        if not name_val and not strict_parsing:
            continue
        nv = name_val

        if len(nv) != 2:
            if strict_parsing:
                raise ValueError(f"Bad query field: {name_val}")
            # Handle case of a control-name with no equal sign
            if keep_blank_values:
                nv.append("")
            else:
                continue

        if len(nv[1]) or keep_blank_values:
            get_name_value(nv[0], nv[1])

    return tokens

