import os
import sys
import glob
import subprocess

old_prefix = sys.prefix
old_exec_prefix = sys.exec_prefix

# Python's virtualenv's activate/deactivate ported from the bash script to Python code.
# https://docs.python.org/3/library/venv.html#:~:text=each%20provided%20path.-,How%20venvs%20work%C2%B6,-When%20a%20Python


def deactivate() -> None:
    """Deactivates the current virtual environment."""
    if "_OLD_VIRTUAL_PATH" in os.environ:
        os.environ["PATH"] = os.environ["_OLD_VIRTUAL_PATH"]
        del os.environ["_OLD_VIRTUAL_PATH"]
    if "_OLD_VIRTUAL_PYTHONHOME" in os.environ:
        os.environ["PYTHONHOME"] = os.environ["_OLD_VIRTUAL_PYTHONHOME"]
        del os.environ["_OLD_VIRTUAL_PYTHONHOME"]
    if "VIRTUAL_ENV" in os.environ:
        del os.environ["VIRTUAL_ENV"]

    sys.prefix = old_prefix
    sys.exec_prefix = old_exec_prefix


def activate(path: str) -> None:
    """Activates the virtual environment at the given path."""
    deactivate()

    virtual_env = os.path.abspath(path)
    os.environ["_OLD_VIRTUAL_PATH"] = os.environ.get("PATH", "")
    os.environ["VIRTUAL_ENV"] = virtual_env

    old_pythonhome = os.environ.pop("PYTHONHOME", None)
    if old_pythonhome:
        os.environ["_OLD_VIRTUAL_PYTHONHOME"] = old_pythonhome

    site_packages = glob.glob(
        os.path.join(virtual_env, "lib", "python*", "site-packages")
    )[0]
    sys.path.insert(0, site_packages)
    sys.prefix = virtual_env
    sys.exec_prefix = virtual_env


def install(*packages: str) -> int:
    pip = os.path.join(sys.prefix, "bin", "pip")
    return subprocess.call([pip, "install", "--require-virtualenv", *packages])


def uninstall(*packages: str) -> int:
    pip = os.path.join(sys.prefix, "bin", "pip")
    return subprocess.call([pip, "uninstall", "--require-virtualenv", "-y", *packages])


def create(path: str) -> int:
    return subprocess.call(["python3", "-m", "venv", path])


def create_default() -> str:
    root = f"{os.environ['HOME']}/Scalpel" if os.environ.get("HOME") else "/tmp/Scalpel"
    scalpel_dir = f"{root}/Scalpel"
    scalpel_venv = f"{scalpel_dir}/venv_default"
    os.makedirs(scalpel_dir, exist_ok=True)
    create(scalpel_venv)
    return scalpel_venv


if __name__ == "__main__":
    activate(create_default())
    uninstall("pdoc", "mitmproxy", "jep")
    install("pdoc", "mitmproxy", "jep")
