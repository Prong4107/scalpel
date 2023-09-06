import sys
from pyscalpel.java import import_java


# Define a default logger to use if for some reason the logger is not initialized
# (e.g. running the script from pdoc)
class Logger:
    """Provides methods for logging messages to the Burp Suite output and standard streams."""

    def all(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}")

    def trace(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}")

    def debug(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}")

    def info(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}")

    def warn(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}")

    def fatal(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}")

    def error(self, msg: str):
        """Prints the message to the standard error

        Args:
            msg (str): The message to print
        """
        print(f"(default): {msg}", file=sys.stderr)


try:
    logger: Logger = import_java("lexfo.scalpel", "ScalpelLogger", Logger)
    logger.all("Imported logger")
except ImportError as ex:
    # logger: Logger = Logger()
    # logger.error("(default): Couldn't import logger")
    # logger.error(str(ex))
    pass
