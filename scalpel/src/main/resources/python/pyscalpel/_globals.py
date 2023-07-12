import sys
from pyscalpel.java.scalpel_types import Context


# Define a default logger to use if for some reason the logger is not initialized
# (e.g. running the script from pdoc)
class DefaultLogger:
    """Debug logger to use if for some reason the logger is not initialized"""

    def all(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def trace(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def debug(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def info(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def warn(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def fatal(self, msg: str):
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def error(self, msg: str):
        """Prints the message to the standard error

        Args:
            msg (str): The message to print
        """
        print(msg, file=sys.stderr)


logger: DefaultLogger = DefaultLogger()

ctx: Context = Context()  # type: ignore
