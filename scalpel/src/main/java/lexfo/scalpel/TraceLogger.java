package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.util.Arrays;

public class TraceLogger {

  static void logExceptionStackTrace(Logging logger, Exception exception) {
    logger.logToError("ERROR:");
    logger.logToError(exception.toString());
    Arrays
      .stream(exception.getStackTrace())
      .forEach(el -> logger.logToError(el.toString()));
  }

  static void logStackTrace(Logging logger) {
    Arrays
      .stream(Thread.currentThread().getStackTrace())
      .forEach(el -> logger.logToError(el.toString()));
  }

  static void logStackTrace(Logging logger, Boolean error) {
    Arrays
      .stream(Thread.currentThread().getStackTrace())
      .forEach(el -> {
        if (error) logger.logToError(el.toString()); else logger.logToOutput(
          el.toString()
        );
      });
  }
}