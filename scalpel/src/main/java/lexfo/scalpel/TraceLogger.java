package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.util.Arrays;

/**
  Provides methods for logging messages to the Burp Suite output and standard streams.
*/
public class TraceLogger {

  /**
    Logs the specified message to the Burp Suite output and standard output.
    @param logger The Logging object to use.
    @param msg The message to log.
  */
  public static void log(Logging logger, String msg) {
    System.out.println(msg);
    logger.logToOutput(msg);
  }

  /**
    Logs the specified message to the Burp Suite error output and standard error.
    @param logger The Logging object to use.
    @param msg The message to log.
  */
  public static void logError(Logging logger, String msg) {
    System.err.println(msg);
    logger.logToError(msg);
  }

  /**
    Logs the specified throwable stack trace to the Burp Suite error output and standard error.
    @param logger The Logging object to use.
    @param throwed The throwable to log.
  */
  public static void logStackTrace(Logging logger, Throwable throwed) {
    logError(logger, "ERROR:");
    logError(logger, throwed.toString());
    Arrays
      .stream(throwed.getStackTrace())
      .forEach(el -> logError(logger, el.toString()));
  }

  /**
    Logs the current thread stack trace to the Burp Suite error output and standard error.
    @param logger The Logging object to use.
  */
  public static void logStackTrace(Logging logger) {
    Arrays
      .stream(Thread.currentThread().getStackTrace())
      .forEach(el -> logError(logger, el.toString()));
  }

  /**
    Logs the current thread stack trace to either the Burp Suite output and standard output or the Burp Suite error output and standard error.
    @param logger The Logging object to use.
    @param error Whether to log to the error output or not.
  */
  public static void logStackTrace(Logging logger, Boolean error) {
    Arrays
      .stream(Thread.currentThread().getStackTrace())
      .forEach(el -> {
        if (error) logError(logger, el.toString()); else logger.logToOutput(
          el.toString()
        );
      });
  }
}
