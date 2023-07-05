import sys


# Define a default logger to use if for some reason the logger is not initialized
# (e.g. running the script from pdoc)
class DefaultLogger:
    """Debug logger to use if for some reason the logger is not initialized"""

    def logToOutput(self, msg: str):  # pylint: disable=invalid-name
        """Prints the message to the standard output

        Args:
            msg (str): The message to print
        """
        print(msg)

    def logToError(self, msg: str):  # pylint: disable=invalid-name
        """Prints the message to the standard error

        Args:
            msg (str): The message to print
        """
        print(msg, file=sys.stderr)


logger: DefaultLogger = DefaultLogger()
