package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.SwingUtilities;
import jep.ClassEnquirer;
import jep.ClassList;
import jep.Interpreter;
import jep.JepConfig;
import jep.SubInterpreter;
import lexfo.scalpel.ScalpelLogger.Level;

/**
 * Responds to requested Python tasks from multiple threads through a task queue handled in a single sepearate thread.
 *
 * <p>The executor is responsible for managing a single global Python interpreter
 * for every script that's being executed.
 *
 * <p>The executor itself is designed to be used concurrently by different threads.
 * It provides a simple interface for submitting tasks to be executed by the script,
 * and blocks each thread until the task has been completed, providing a thread-safe
 * way to ensure that the script's state remains consistent.
 *
 * <p>Tasks are submitted as function calls with optional arguments and keyword
 * arguments. Each function call is executed in the script's global context, and
 * the result of the function is returned to the JVM thread that submitted the
 * task.
 *
 * <p>The executor is capable of restarting the Python interpreter when the
 * script file changes on disk. This ensures that any modifications made to the
 * script are automatically loaded by the executor without requiring a manual
 * restart of the extension.
 *
 */
public class ScalpelExecutor {

	/**
	 * A custom ClassEnquirer for the Jep interpreter used by the script executor.
	 */
	private class CustomEnquirer implements ClassEnquirer {

		/**
		 * The base ClassEnquirer to use.
		 */
		private ClassList base;

		/**
		 * Constructs a new CustomEnquirer object.
		 */
		CustomEnquirer() {
			this.base = ClassList.getInstance();
		}

		/**
		 * Gets the names of all the classes in a package.
		 *
		 * @param pkg the name of the package.
		 * @return an array of the names of the classes in the package.
		 */
		public String[] getClassNames(String pkg) {
			return base.getClassNames(pkg);
		}

		/**
		 * Gets the names of all the sub-packages of a package.
		 *
		 * @param p the name of the package.
		 * @return an array of the names of the sub-packages of the package.
		 */
		public String[] getSubPackages(String p) {
			return base.getSubPackages(p);
		}

		/**
		 * Determines whether a string represents a valid Java package.
		 *
		 * @param s the string to check.
		 * @return true if the string represents a valid Java package, false otherwise.
		 */
		public boolean isJavaPackage(String s) {
			// https://github.com/ninia/jep/issues/347
			if (s.equals("lexfo") || s.equals("lexfo.scalpel")) {
				ScalpelLogger.log("Returning true");
				return true;
			}
			return base.isJavaPackage(s);
		}
	}

	/**
	 * A class representing a task to be executed by the Scalpel script.
	 */
	private class Task {

		/**
		 * The name of the task.
		 */
		private String name;

		/**
		 * The arguments passed to the task.
		 */
		private Object[] args;

		/**
		 * Whether the task has been completed. (Used to break out of the awaitResult() loop in case of failure.)
		 */
		private Boolean finished = false;

		/**
		 * The keyword arguments passed to the task.
		 */
		private Map<String, Object> kwargs;

		/**
		 * An optional object containing the result of the task, if it has been completed.
		 */
		private Optional<Object> result = Optional.empty();

		/**
		 * Constructs a new Task object.
		 *
		 * @param name the name of the task.
		 * @param args the arguments passed to the task.
		 * @param kwargs the keyword arguments passed to the task.
		 */
		public Task(String name, Object[] args, Map<String, Object> kwargs) {
			this.name = name;
			this.args = args;
			this.kwargs = kwargs;

			ScalpelLogger.log("Created task: " + name);
		}

		/**
		 * Add the task to the queue and wait for it to be completed by the task thread.
		 *
		 * @return the result of the task.
		 */
		public synchronized Optional<Object> awaitResult() {
			// Log before awaiting to debug potential deadlocks.
			ScalpelLogger.log(
				ScalpelLogger.Level.DEBUG,
				"Awaiting task: " + name
			);

			// Acquire the lock on the Task object.
			synchronized (this) {
				// Ensure we return only when result has been set
				// (apparently wait() might return even if notify hasn't been called for some weird software and hardware issues)
				while (!isFinished()) {
					// Wrap the wait in try/catch to handle InterruptedException.
					try {
						// Wait for the object to be notified.
						this.wait(1000);

						if (!isFinished()) {
							// Warn the user that a task is taking a long time.
							ScalpelLogger.log(
								Level.WARN,
								"Task " + name + " is still waiting..."
							);
						}
					} catch (InterruptedException e) {
						// Log the error.
						ScalpelLogger.error("Task " + name + "interrupted:");

						// Log the stack trace.
						ScalpelLogger.logStackTrace(e);
					}
				}
			}

			ScalpelLogger.log(
				ScalpelLogger.Level.DEBUG,
				"Finished awaiting task: " + name
			);
			// Return the awaited result.
			return result;
		}

		public Boolean isFinished() {
			return finished;
		}
	}

	/**
	 * The MontoyaApi object to use for sending and receiving HTTP messages.
	 */
	private final MontoyaApi API;

	/**
	 * The path of the Scalpel script that will be passed to the framework.
	 */
	private Optional<File> script = Optional.empty();

	/**
	 * The path of the Scalpel framework that will be used to execute the script.
	 */
	private Optional<File> framework = Optional.empty();

	/**
	 * The task runner thread.
	 */
	private Thread runner;

	/**
	 * The Python task queue.
	 */
	private final Queue<Task> tasks = new LinkedBlockingQueue<>();

	/**
	 * The timestamp of the last recorded modification to the script file.
	 */
	private long lastScriptModificationTimestamp = -1;

	/**
	 * The timestamp of the last recorded modification to the framework file.
	 */
	private long lastFrameworkModificationTimestamp = -1;

	private long lastConfigModificationTimestamp = -1;

	/**
	 * Flag indicating whether the task runner loop is running.
	 */
	private Boolean isRunnerAlive = false;

	/**
	 * The ScalpelUnpacker object to get the ressources paths.
	 */
	private final ScalpelUnpacker unpacker;

	private final Config config;

	private Optional<ScalpelEditorProvider> editorProvider = Optional.empty();

	/**
	 * Constructs a new ScalpelExecutor object.
	 *
	 * @param API the MontoyaApi object to use for sending and receiving HTTP messages.
	 * @param unpacker the ScalpelUnpacker object to use for getting the ressources paths.
	 * @param config the Config object to use for getting the configuration values.
	 */
	public ScalpelExecutor(
		MontoyaApi API,
		ScalpelUnpacker unpacker,
		Config config
	) {
		// Store Montoya API object
		this.API = API;

		// Store the unpacker
		this.unpacker = unpacker;

		// Keep a reference to the config
		this.config = config;

		// Create a File wrapper from the script path.
		this.script = Optional.ofNullable(new File(config.getUserScriptPath()));

		this.framework =
			Optional.ofNullable(new File(config.getFrameworkPath()));

		this.lastConfigModificationTimestamp = config.getLastModified();
		this.framework.ifPresent(f ->
				this.lastFrameworkModificationTimestamp = f.lastModified()
			);
		this.script.ifPresent(s ->
				this.lastScriptModificationTimestamp = s.lastModified()
			);

		// Launch task thread.
		this.script.ifPresent(s -> this.runner = this.launchTaskRunner());
	}

	/**
	 * Adds a new task to the queue of tasks to be executed by the script.
	 *
	 * @param name the name of the python function to be called.
	 * @param args the arguments to pass to the python function.
	 * @param kwargs the keyword arguments to pass to the python function.
	 * @param rejectOnReload reject the task when the runner is reloading.
	 * @return a Task object representing the added task.
	 */
	private Task addTask(
		String name,
		Object[] args,
		Map<String, Object> kwargs,
		boolean rejectOnReload
	) {
		// Create task object.
		final Task task = new Task(name, args, kwargs);

		synchronized (tasks) {
			// Ensure the runner is alive.
			if (isRunnerAlive) {
				// Queue the task.
				tasks.add(task);

				// Release the runner's lock.
				tasks.notifyAll();
			} else if (rejectOnReload) {
				// The runner is dead, reject this task to avoid blocking Burp when awaiting.
				task.result = Optional.empty();
				task.finished = true;
			}
		}

		// Return the queued or rejected task.
		return task;
	}

	/**
	 * Adds a new task to the queue of tasks to be executed by the script.
	 *
	 * @param name the name of the python function to be called.
	 * @param args the arguments to pass to the python function.
	 * @param kwargs the keyword arguments to pass to the python function.
	 * @return a Task object representing the added task.
	 */
	private Task addTask(
		String name,
		Object[] args,
		Map<String, Object> kwargs
	) {
		return addTask(name, args, kwargs, true);
	}

	/**
	 * Awaits the result of a task.
	 *
	 * @param <T> the type of the result of the task.
	 * @param name the name of the python function to be called.
	 * @param args the arguments to pass to the python function.
	 * @param kwargs the keyword arguments to pass to the python function.
	 * @return an Optional object containing the result of the task, or empty if the task was rejected or failed.
	 */
	@SuppressWarnings({ "unchecked" })
	private final <T> Optional<T> awaitTask(
		String name,
		Object[] args,
		Map<String, Object> kwargs,
		Class<T> expectedClass
	) {
		// Queue a new task and await it's result.
		final Optional<Object> result = addTask(name, args, kwargs)
			.awaitResult();

		if (result.isPresent()) {
			try {
				Object rawResult = result.get();
				T castResult = (T) rawResult;

				ScalpelLogger.log(
					"Successfully cast " +
					UnObfuscator.getClassName(rawResult) +
					" to " +
					// expectedClass.getSimpleName()
					UnObfuscator.getClassName(castResult)
				);
				// Ensure the result can be cast to the expected type.
				return Optional.of(castResult);
			} catch (ClassCastException e) {
				ScalpelLogger.error("Failed casting " + name + "'s result:");
				// Log the error stack trace.
				ScalpelLogger.logStackTrace(e);
			}
		}

		// Return an empty object.
		return Optional.empty();
	}

	/**
	 * Checks if the script file has been modified since the last check.
	 *
	 * @return true if the script file has been modified since the last check, false otherwise.
	 */
	private Boolean hasScriptChanged() {
		return script
			.map(script -> {
				// Check if the last modification date has changed since last record.
				final Boolean hasChanged =
					lastScriptModificationTimestamp != script.lastModified();

				// Update the last modification date record.
				lastScriptModificationTimestamp = script.lastModified();

				this.script =
					Optional.ofNullable(new File(config.getUserScriptPath()));

				// Return the check result.²
				return hasChanged;
			})
			.orElse(false);
	}

	private final Boolean hasConfigChanged() {
		final long currentConfigModificationTimestamp = config.getLastModified();

		// Check if the last modification date has changed since last record.
		final Boolean hasChanged =
			lastConfigModificationTimestamp !=
			currentConfigModificationTimestamp;

		// Update the last modification date record.
		lastConfigModificationTimestamp = currentConfigModificationTimestamp;

		// Return the check result.
		return hasChanged;
	}

	/**
	 * Checks if either the framework or user script file has been modified since the last check.
	 *
	 * @return true if either the framework or user script file has been modified since the last check, false otherwise.
	 */
	private Boolean mustReload() {
		// Use | instead of || to avoid lazy evaluation preventing all modification timestamp from being updated.
		return (
			hasFrameworkChanged() | hasScriptChanged() | hasConfigChanged()
		);
	}

	/**
	 * Checks if the framework file has been modified since the last check.
	 *
	 * @return true if the framework file has been modified since the last check, false otherwise.
	 */
	private final Boolean hasFrameworkChanged() {
		return framework
			.map(framework -> {
				// Check if the last modification date has changed since last record.
				final Boolean hasChanged =
					lastFrameworkModificationTimestamp !=
					framework.lastModified();

				// Update the last modification date record.
				lastFrameworkModificationTimestamp = framework.lastModified();

				this.framework =
					Optional.ofNullable(new File(config.getFrameworkPath()));

				// Return the check result.²
				return hasChanged;
			})
			.orElse(false);
	}

	public void setEditorsProvider(ScalpelEditorProvider provider) {
		this.editorProvider = Optional.of(provider);
		provider.resetEditors();
	}

	// WARN: Declaring this method as synchronized cause deadlocks.
	private void taskLoop() {
		ScalpelLogger.log("Starting task loop.");

		try {
			// Instantiate the interpreter.
			final SubInterpreter interp = initInterpreter();
			isRunnerAlive = true;

			while (true) {
				// Relaunch interpreter when files have changed (hot reload).
				if (mustReload()) {
					ScalpelLogger.log(
						Level.INFO,
						"Config or Python files have changed, reloading interpreter..."
					);
					break;
				}

				synchronized (tasks) {
					ScalpelLogger.log(
						ScalpelLogger.Level.DEBUG,
						"Runner waiting for notifications."
					);

					// Extract the oldest pending task from the queue.
					final Task task = tasks.poll();

					// Ensure a task was polled or poll again.
					if (task == null) {
						// Release the lock and wait for new tasks.
						tasks.wait();
						continue;
					}

					ScalpelLogger.log("Processing task: " + task.name);
					try {
						// Invoke Python function and get the returned value.
						final Object pythonResult = interp.invoke(
							task.name,
							task.args,
							task.kwargs
						);

						ScalpelLogger.log("Executed task: " + task.name);

						if (pythonResult != null) {
							task.result = Optional.of(pythonResult);
						}
					} catch (Exception e) {
						task.result = Optional.empty();

						if (!e.getMessage().contains("Unable to find object")) {
							ScalpelLogger.error("Error in task loop:");
							ScalpelLogger.logStackTrace(e);
						}
					}
					ScalpelLogger.log(
						ScalpelLogger.Level.DEBUG,
						"Processed task"
					);

					// Log the result value.
					ScalpelLogger.log(
						ScalpelLogger.Level.TRACE,
						String.valueOf(task.result.orElse("null"))
					);

					task.finished = true;

					synchronized (task) {
						// Wake threads awaiting the task.
						task.notifyAll();

						ScalpelLogger.log("Notified " + task.name);
					}

					// Sleep the thread while there isn't any new tasks
					tasks.wait();
				}
			}
		} catch (Exception e) {
			// The task loop has crashed, log the stack trace.
			ScalpelLogger.logStackTrace(e);
		}
		// Log the error.
		ScalpelLogger.log("Task loop has crashed");

		isRunnerAlive = false;

		// Relaunch the task thread
		this.runner = launchTaskRunner();
	}

	/**
	 * Launches the task runner thread.
	 *
	 * @return the launched thread.
	 */
	private Thread launchTaskRunner() {
		// Instantiate the task runner thread.
		final var thread = new Thread(this::taskLoop, "ScalpelRunnerLoop");

		// Start the task runner thread.
		thread.start();

		// Force editor tabs recreation
		// WARN: .resetEditors() depends on the runner loop, do not call it inside of it
		this.editorProvider.ifPresent(e ->
				SwingUtilities.invokeLater(() -> {
					while (!isRunnerAlive) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException exc) {}
					}
					e.resetEditors();
				})
			);

		// Return the running thread.
		return thread;
	}

	private String getDefaultIncludePath() {
		try {
			return Venv.getSitePackagesPath(Config.getDefaultVenv()).toString();
		} catch (IOException e) {
			ScalpelLogger.error(
				"Could not find a default include path for JEP"
			);
		}
		return "";
	}

	/**
	 * Initializes the interpreter.
	 *
	 * @return the initialized interpreter.
	 */
	private SubInterpreter initInterpreter() {
		try {
			return framework
				.map(framework -> {
					// Add a default include path so JEP can be loaded
					String defaultIncludePath = getDefaultIncludePath();

					// Instantiate a Python interpreter.
					final SubInterpreter interp = new SubInterpreter(
						new JepConfig()
							.setClassEnquirer(new CustomEnquirer())
							.addIncludePaths(
								defaultIncludePath,
								unpacker.getPythonPath()
							)
					);

					var burpEnv = new HashMap<>(10);

					// Make the Montoya API object accessible in Python
					burpEnv.put("API", API);

					// Set the framework's filename to corresponding Python variable
					// This isn't set by JEP, we have to do it ourselves.
					burpEnv.put("file", framework.getAbsolutePath());

					// Add logger global to be able to log to Burp from Python.
					burpEnv.put("logger", new ScalpelLogger());

					// Set the path to the user script that will define the actual callbacks.
					burpEnv.put(
						"user_script",
						script.orElseThrow().getAbsolutePath()
					);

					burpEnv.put("framework", framework.getAbsolutePath());

					// Pass the selected venv path so it can be activated by the framework.
					burpEnv.put("venv", config.getSelectedVenvPath());

					interp.set("__scalpel__", burpEnv);

					// Run the framework (wraps the user script)
					interp.runScript(framework.getAbsolutePath());

					// Return the initialized interpreter.
					return interp;
				})
				.orElseThrow();
		} catch (Exception e) {
			ScalpelLogger.error("Failed to instantiate interpreter:");
			ScalpelLogger.logStackTrace(e);
			throw e;
		}
	}

	/**
	 * Evaluates the given script and returns the output.
	 *
	 * @param scriptContent the script to evaluate.
	 * @return the output of the script.
	 */
	public String[] evalAndCaptureOutput(String scriptContent) {
		try (Interpreter interp = initInterpreter()) {
			// Running Python instructions on the fly.
			// https://github.com/t2y/jep-samples/blob/master/src/HelloWorld.java
			interp.exec(
				"""
    from io import StringIO
    import sys
    temp_out = StringIO()
    temp_err = StringIO()
    sys.stdout = temp_out
    sys.stderr = temp_err
    """
			);
			Optional<String> exceptionMessage = Optional.empty();
			try {
				interp.exec(scriptContent);
			} catch (Exception e) {
				final String stackTrace = Arrays
					.stream(e.getStackTrace())
					.map(StackTraceElement::toString)
					.reduce((a, b) -> a + "\n" + b)
					.orElse("No stack trace.");

				final String msgAndTrace = e.getMessage() + "\n" + stackTrace;

				exceptionMessage = Optional.of(msgAndTrace);
			}
			interp.exec("captured_out = temp_out.getvalue()");
			interp.exec("captured_err = temp_err.getvalue()");

			final String capturedOut = (String) interp.getValue("captured_out");
			final String capturedErr = (String) interp.getValue(
				"captured_err"
			) +
			exceptionMessage.map(msg -> "\n\n" + msg).orElse("");
			ScalpelLogger.all(
				String.format(
					"Executed:\n%s\nOutput:\n%s\nErr:%s\n",
					scriptContent,
					capturedOut,
					capturedErr,
					null
				)
			);
			return new String[] { capturedOut, capturedErr };
		}
	}

	/**
	 * Returns the name of the corresponding Python callback for the given message intercepted by Proxy.
	 *
	 * @param <T> the type of the message.
	 * @param msg the message to get the callback name for.
	 * @return the name of the corresponding Python callback.
	 */
	private static final <T extends HttpMessage> String getMessageCbName(
		T msg
	) {
		if (
			msg instanceof HttpRequest || msg instanceof HttpRequestToBeSent
		) return Constants.FRAMEWORK_REQ_CB_NAME;
		if (
			msg instanceof HttpResponse || msg instanceof HttpResponseReceived
		) return Constants.FRAMEWORK_RES_CB_NAME;
		throw new RuntimeException("Passed wrong type to geMessageCbName");
	}

	/**
	 * Calls the corresponding Python callback for the given message intercepted by Proxy.
	 *
	 * @param <T> the type of the message.
	 * @param msg the message to call the callback for.
	 * @return the result of the callback.
	 */
	@SuppressWarnings({ "unchecked" })
	public <T extends HttpMessage> Optional<T> callIntercepterCallback(
		T msg,
		HttpService service
	) {
		// Call the corresponding Python callback and add a debug HTTP header.
		return safeJepInvoke(
			getMessageCbName(msg),
			new Object[] { msg, service },
			Map.of(),
			(Class<T>) msg.getClass()
		);
	}

	/**
	 * Returns the name of the corresponding Python callback for the given tab.
	 *
	 * @param tabName the name of the tab.
	 * @param isRequest whether the tab is a request tab.
	 * @param isInbound whether the callback is use to modify the request back or update the editor's content.
	 * @return the name of the corresponding Python callback.
	 */
	private static final String getEditorCallbackName(
		Boolean isRequest,
		Boolean isInbound
	) {
		// Either req_ or res_ depending if it is a request or a response.
		final var editPrefix = isRequest
			? Constants.FRAMEWORK_REQ_EDIT_PREFIX
			: Constants.FRAMEWORK_RES_EDIT_PREFIX;

		// Either in_ or out_ depending on context.
		final var directionPrefix = isInbound
			? Constants.IN_SUFFIX
			: Constants.OUT_SUFFIX;

		// Concatenate the prefixes
		final var cbName = editPrefix + directionPrefix;

		// Return the callback Python function name.
		return cbName;
	}

	/**
	 * Calls the given Python function with the given arguments and keyword arguments.
	 *
	 * @param <T> the expected class of the returned value.
	 * @param name the name of the Python function to call.
	 * @param args the arguments to pass to the function.
	 * @param kwargs the keyword arguments to pass to the function.
	 * @param expectedClass the expected class of the returned value.
	 * @return the result of the function call.
	 */
	public synchronized <T extends Object> Optional<T> safeJepInvoke(
		String name,
		Object[] args,
		Map<String, Object> kwargs,
		Class<T> expectedClass
	) {
		// Create a task and await the result.
		return awaitTask(name, args, kwargs, expectedClass);
	}

	/**
	 * Calls the given Python function with the given argument.
	 *
	 * @param <T> the expected class of the returned value.
	 * @param name the name of the Python function to call.
	 * @param arg the argument to pass to the function.
	 * @param expectedClass the expected class of the returned value.
	 * @return the result of the function call.
	 */

	public <T> Optional<T> safeJepInvoke(
		String name,
		Object arg,
		Class<T> expectedClass
	) {
		// Call base safeJepInvoke with a single argument and a logger as default kwarg.
		return safeJepInvoke(
			name,
			new Object[] { arg },
			Map.of(),
			expectedClass
		);
	}

	/**
	 * Calls the given Python function without any argument.
	 *
	 * @param <T> the expected class of the returned value.
	 * @param name the name of the Python function to call.
	 * @param arg the argument to pass to the function.
	 * @param expectedClass the expected class of the returned value.
	 * @return the result of the function call.
	 */

	public <T> Optional<T> safeJepInvoke(String name, Class<T> expectedClass) {
		return safeJepInvoke(name, new Object[] {}, Map.of(), expectedClass);
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param <T> the expected class of the returned value.
	 * @param params the parameters to pass to the callback.
	 * @param isRequest whether the tab is a request tab.
	 * @param isInbound whether the callback is use to modify the request back or update the editor's content.
	 * @param tabName the name of the tab.
	 * @param expectedClass the expected class of the returned value.
	 * @return the result of the callback.
	 */
	public <T> Optional<T> callEditorCallback(
		Object[] params,
		Boolean isRequest,
		Boolean isInbound,
		String tabName,
		Class<T> expectedClass
	) {
		var suffix = tabName.isEmpty() ? tabName : "_" + tabName;

		// Call safeJepInvoke with the corresponding function name
		return safeJepInvoke(
			getEditorCallbackName(isRequest, isInbound),
			params,
			Map.of("callback_suffix", suffix),
			expectedClass
		);
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param <T> the expected class of the returned value.
	 * @param param the parameter to pass to the callback.
	 * @param isRequest whether the tab is a request tab.
	 * @param isInbound whether the callback is use to modify the request back or update the editor's content.
	 * @param tabName the name of the tab.
	 * @param expectedClass the expected class of the returned value.
	 * @return the result of the callback.
	 */
	public <T> Optional<T> callEditorCallback(
		Object param,
		HttpService service,
		Boolean isRequest,
		Boolean isInbound,
		String tabName,
		Class<T> expectedClass
	) {
		// Call base method with a single parameter.
		return callEditorCallback(
			new Object[] { param, service },
			isRequest,
			isInbound,
			tabName,
			expectedClass
		);
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param msg the message to pass to the callback.
	 * @param isInbound whether the callback is use to modify the request back or update the editor's content.
	 * @param tabName the name of the tab.
	 * @return the result of the callback.
	 */
	public Optional<ByteArray> callEditorCallback(
		HttpMessage msg,
		HttpService service,
		Boolean isInbound,
		String tabName
	) {
		return callEditorCallback(
			msg,
			service,
			msg instanceof HttpRequest,
			isInbound,
			tabName,
			byte[].class
		)
			.flatMap(bytes -> Optional.of(ByteArray.byteArray(bytes)));
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param msg the message to pass to the callback.
	 * @param byteArray the byte array to pass to the callback (editor content).
	 * @param isInbound whether the callback is use to modify the request back or update the editor's content.
	 * @param tabName the name of the tab.
	 * @return the result of the callback.
	 */
	public Optional<Object> callEditorCallback(
		HttpMessage msg,
		HttpService service,
		ByteArray byteArray,
		Boolean isInbound,
		String tabName
	) {
		return callEditorCallback(
			new Object[] {
				msg,
				service,
				PythonUtils.toPythonBytes(byteArray.getBytes()),
			},
			msg instanceof HttpRequest,
			isInbound,
			tabName,
			byte[].class
		)
			.flatMap(bytes -> Optional.of(ByteArray.byteArray(bytes)));
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param req the message to pass to the callback.
	 * @param byteArray the byte array to pass to the callback (editor content).
	 * @param tabName the name of the tab.
	 * @return the result of the callback.
	 */
	public Optional<ByteArray> callEditorCallbackInRequest(
		HttpRequest req,
		HttpService service,
		String tabName
	) {
		return callEditorCallback(
			new Object[] { req, service },
			req instanceof HttpRequest,
			true,
			tabName,
			byte[].class
		)
			.flatMap(bytes -> Optional.of(ByteArray.byteArray(bytes)));
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param res the message to pass to the callback.
	 * @param byteArray the byte array to pass to the callback (editor content).
	 * @param tabName the name of the tab.
	 * @return the result of the callback.
	 */
	public Optional<ByteArray> callEditorCallbackInResponse(
		HttpResponse res,
		HttpRequest req,
		HttpService service,
		String tabName
	) {
		return callEditorCallback(
			new Object[] { res, req, service },
			false,
			true,
			tabName,
			byte[].class
		)
			.flatMap(bytes -> Optional.of(ByteArray.byteArray(bytes)));
	}

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param msg the message to pass to the callback.
	 * @param byteArray the byte array to pass to the callback (editor content).
	 * @param tabName the name of the tab.
	 * @return the result of the callback.
	 */
	public Optional<HttpRequest> callEditorCallbackOutRequest(
		HttpRequest req,
		HttpService service,
		ByteArray byteArray,
		String tabName
	) {
		return callEditorCallback(
			new Object[] {
				req,
				service,
				PythonUtils.toPythonBytes(byteArray.getBytes()),
			},
			true,
			false,
			tabName,
			HttpRequest.class
		);
	}

	// TODO: update docstrings

	/**
	 * Calls the corresponding Python callback for the given tab.
	 *
	 * @param msg the message to pass to the callback.
	 * @param byteArray the byte array to pass to the callback (editor content).
	 * @param tabName the name of the tab.
	 * @return the result of the callback.
	 */
	public Optional<HttpResponse> callEditorCallbackOutResponse(
		HttpResponse res,
		HttpRequest req,
		HttpService service,
		ByteArray byteArray,
		String tabName
	) {
		return callEditorCallback(
			new Object[] {
				res,
				req,
				service,
				PythonUtils.toPythonBytes(byteArray.getBytes()),
			},
			false,
			false,
			tabName,
			HttpResponse.class
		);
	}

	@SuppressWarnings({ "unchecked" })
	public List<String> getCallables() throws RuntimeException {
		// Jep doesn't offer any way to list functions, so we have to implement it Python side.
		return this.safeJepInvoke(Constants.GET_CB_NAME, List.class)
			.orElseThrow(() ->
				new RuntimeException(Constants.GET_CB_NAME + " was not found.")
			);
	}
}
