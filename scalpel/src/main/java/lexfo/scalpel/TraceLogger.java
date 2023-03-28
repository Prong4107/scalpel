package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.util.Arrays;

/**
  Provides methods for logging messages to the Burp Suite output and standard streams.
*/
public class TraceLogger {

	/**
	 * Log levels used to filtrate logs by weight
	 * Useful for debugging.
	 */
	public enum Level {
		TRACE(1),
		DEBUG(2),
		INFO(3),
		WARN(4),
		ERROR(5),
		FATAL(6),
		ALL(7);

		private int value;

		private Level(int value) {
			this.value = value;
		}

		public int value() {
			return value;
		}
	}

	/**
	 * Configured log level
	 * TODO: Add to configuration.
	 */
	private static Level loggerLevel = Level.TRACE;

	/**
    Logs the specified message to the Burp Suite output and standard output.
    @param logger The Logging object to use.
    @param msg The message to log.
  */
	public static void log(Logging logger, Level level, String msg) {
		if (loggerLevel.value() <= level.value()) {
			System.out.println(msg);
			logger.logToOutput(msg);
		}
	}

	/**
    Logs the specified message to the Burp Suite output and standard output.
    @param logger The Logging object to use.
    @param msg The message to log.
  */
	public static void log(Logging logger, String msg) {
		log(logger, Level.TRACE, msg);
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
				if (error) logError(
					logger,
					el.toString()
				); else logger.logToOutput(el.toString());
			});
	}
}
