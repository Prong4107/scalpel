package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.util.Arrays;

public class TraceLogger {

  static void log(Logging logger, String msg) {
    System.out.println(msg);
    logger.logToOutput(msg);
  }

  static void logError(Logging logger, String msg) {
    System.err.println(msg);
    logger.logToError(msg);
  }
  static void logStackTrace(Logging logger, Throwable throwed) {
    logError(logger, "ERROR:");
    logError(logger, throwed.toString());
    Arrays
      .stream(throwed.getStackTrace())
      .forEach(el -> logError(logger, el.toString()));
  }

  static void logStackTrace(Logging logger) {
    Arrays
      .stream(Thread.currentThread().getStackTrace())
      .forEach(el -> logError(logger, el.toString()));
  }

  static void logStackTrace(Logging logger, Boolean error) {
    Arrays
      .stream(Thread.currentThread().getStackTrace())
      .forEach(el -> {
        if (error) logError(logger, el.toString()); else logger.logToOutput(
          el.toString()
        );
      });
  }
}
