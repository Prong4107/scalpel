package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.util.Arrays;

public class ExceptionLogger {

  static void logStackTrace(Logging logger, Exception exception) {
    Arrays
      .stream(exception.getStackTrace())
      .forEach(el -> logger.logToError(el.toString()));
  }
}
