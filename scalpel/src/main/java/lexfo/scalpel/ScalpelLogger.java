package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.util.Arrays;

/**
 * Provides methods for logging messages to the Burp Suite output and standard streams.
 */
public class ScalpelLogger {

	/**
	 * Log levels used to filtrate logs by weight
	 * Useful for debugging.
	 */
	public enum Level {
		TRACE(1),
		DEBUG(2),
		INFO(3),
		WARN(4),
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

	private static Logging logger = null;

	/**
	 * Set the Burp logger instance to use.
	 * @param logger
	 */
	public static void setLogger(Logging logger) {
		ScalpelLogger.logger = logger;
	}

	/**
	 * Configured log level
	 * TODO: Add to configuration.
	 */
	private static Level loggerLevel = Level.INFO;

	/**
	 * Logs the specified message to the Burp Suite output and standard output at the TRACE level.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void trace(String msg) {
		log(Level.TRACE, msg);
	}

	/**
	 * Logs the specified message to the Burp Suite output and standard output at the DEBUG level.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void debug(String msg) {
		log(Level.DEBUG, msg);
	}

	/**
	 * Logs the specified message to the Burp Suite output and standard output at the INFO level.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void info(String msg) {
		log(Level.INFO, msg);
	}

	/**
	 * Logs the specified message to the Burp Suite output and standard output at the WARN level.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void warn(String msg) {
		log(Level.WARN, msg);
	}


	/**
	 * Logs the specified message to the Burp Suite output and standard output at the FATAL level.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void fatal(String msg) {
		log(Level.FATAL, msg);
	}

	/**
	 * Logs the specified message to the Burp Suite output and standard output.
	 *
	 * @param logger The Logging object to use.
	 * @param level  The log level.
	 * @param msg    The message to log.
	 */
	public static void log(Level level, String msg) {
		if (loggerLevel.value() <= level.value()) {
			System.out.println(msg);
			if (logger != null) {
				logger.logToOutput(msg);
			}
		}
	}

	/**
	 * Logs the specified message to the Burp Suite output and standard output at the TRACE level.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void log(String msg) {
		log(Level.TRACE, msg);
	}

	/**
	 * Logs the specified message to the Burp Suite error output and standard error.
	 *
	 * @param logger The Logging object to use.
	 * @param msg    The message to log.
	 */
	public static void error(String msg) {
		System.err.println(msg);
		if (logger != null) {
			logger.logToError(msg);
		}
	}

	/**
	 * Logs the specified throwable stack trace to the Burp Suite error output and standard error.
	 *
	 * @param logger  The Logging object to use.
	 * @param throwed The throwable to log.
	 */
	public static void logStackTrace(Throwable throwed) {
		error("ERROR:");
		error(throwed.getMessage());
		error(throwed.toString());
		Arrays
			.stream(throwed.getStackTrace())
			.forEach(el -> error(el.toString()));
	}

	/**
	 * Logs the current thread stack trace to the Burp Suite error output and standard error.
	 *
	 * @param logger The Logging object to use.
	 */
	public static void logStackTrace() {
		Arrays
			.stream(Thread.currentThread().getStackTrace())
			.forEach(el -> error(el.toString()));
	}

	/**
	 * Logs the current thread stack trace to either the Burp Suite output and standard output or the Burp Suite error output and standard error.
	 *
	 * @param logger The Logging object to use.
	 * @param error  Whether to log to the error output or not.
	 */
	public static void logStackTrace(Boolean error) {
		Arrays
			.stream(Thread.currentThread().getStackTrace())
			.forEach(el -> {
				if (error) error(el.toString()); else logger.logToOutput(
					el.toString()
				);
			});
	}

	public static void all(String msg) {
		log(Level.ALL, msg);
	}
}
