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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jep.ClassEnquirer;
import jep.ClassList;
import jep.Interpreter;
import jep.JepConfig;
import jep.SubInterpreter;

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
		public synchronized Optional<Object> await() {
			// Log this before awaiting to debug potential deadlocks.
			ScalpelLogger.trace("Awaiting task: " + name);

			// Acquire the lock on the Task object.
			synchronized (this) {
				// Ensure we return only when result has been set
				// (apparently wait() might return even if notify hasn't been called for some weird software and hardware issues)
				while (isEnabled && !isFinished()) {
					// Wrap the wait in try/catch to handle InterruptedException.
					try {
						// Wait for the object to be notified.
						this.wait(1000);

						if (!isFinished()) {
							// Warn the user that a task is taking a long time.
							ScalpelLogger.warn(
								"Task " + name + " is still waiting..."
							);
						}
					} catch (InterruptedException e) {
						// Log the error.
						ScalpelLogger.error("Task " + name + " interrupted:");

						// Log the stack trace.
						ScalpelLogger.logStackTrace(e);
					}
				}
			}

			ScalpelLogger.trace("Finished awaiting task: " + name);
			// Return the awaited result.
			return result;
		}

		public Boolean isFinished() {
			return finished;
		}

		public synchronized void then(Consumer<Object> callback) {
			Async.run(() -> this.await().ifPresent(callback));
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

	private Boolean isRunnerStarting = true;

	private final Config config;

	private Optional<ScalpelEditorProvider> editorProvider = Optional.empty();

	private Boolean isEnabled = true;

	/**
	 * Constructs a new ScalpelExecutor object.
	 *
	 * @param API the MontoyaApi object to use for sending and receiving HTTP messages.
	 * @param config the Config object to use for getting the configuration values.
	 */
	public ScalpelExecutor(MontoyaApi API, Config config) {
		// Store Montoya API object
		this.API = API;

		// Keep a reference to the config
		this.config = config;

		// Create a File wrapper from the script path.
		this.script =
			Optional
				.ofNullable(config.getUserScriptPath())
				.map(Path::toFile)
				.filter(File::exists);

		this.framework =
			Optional
				.ofNullable(config.getFrameworkPath())
				.map(Path::toFile)
				.filter(File::exists);

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

	public boolean isEnabled() {
		return this.isEnabled;
	}

	public void enable() {
		this.isEnabled = true;
	}

	public void disable() {
		this.isEnabled = false;
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
			if (isEnabled && (isRunnerAlive || isRunnerStarting)) {
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
		final Optional<Object> result = addTask(name, args, kwargs).await();

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
			.map(File::lastModified)
			.map(m -> lastScriptModificationTimestamp != m)
			.orElse(false);
	}

	private Boolean hasConfigChanged() {
		return config.getLastModified() != lastConfigModificationTimestamp;
	}

	private void resetChangeIndicators() {
		this.framework =
			Optional
				.ofNullable(config.getFrameworkPath())
				.map(Path::toFile)
				.filter(File::exists);

		framework.ifPresent(f ->
			lastFrameworkModificationTimestamp = f.lastModified()
		);

		this.script =
			Optional
				.ofNullable(config.getUserScriptPath())
				.map(Path::toFile)
				.filter(File::exists);

		// Update the last modification date record.
		script
			.map(File::lastModified)
			.ifPresent(f -> lastScriptModificationTimestamp = f);

		// Update the last modification date record.
		lastConfigModificationTimestamp = config.getLastModified();
	}

	/**
	 * Checks if either the framework or user script file has been modified since the last check.
	 *
	 * @return true if either the framework or user script file has been modified since the last check, false otherwise.
	 */
	private Boolean mustReload() {
		return (
			hasFrameworkChanged() || hasScriptChanged() || hasConfigChanged()
		);
	}

	/**
	 * Checks if the framework file has been modified since the last check.
	 *
	 * @return true if the framework file has been modified since the last check, false otherwise.
	 */
	private final Boolean hasFrameworkChanged() {
		return framework
			.map(File::lastModified)
			.map(m -> m != lastFrameworkModificationTimestamp)
			.orElse(false);
	}

	public void setEditorsProvider(ScalpelEditorProvider provider) {
		this.editorProvider = Optional.of(provider);
		provider.resetEditorsAsync();
	}

	public synchronized void notifyEventLoop() {
		synchronized (tasks) {
			tasks.notifyAll();
		}
	}

	// WARN: Declaring this method as synchronized cause deadlocks.
	private void taskLoop() {
		ScalpelLogger.info("Starting task loop.");

		isRunnerStarting = true;
		try {
			// Instantiate the interpreter.
			final SubInterpreter interp = initInterpreter();
			isRunnerAlive = true;
			isRunnerStarting = false;

			while (true) {
				// Relaunch interpreter when files have changed (hot reload).
				if (mustReload()) {
					ScalpelLogger.info(
						"Config or Python files have changed, reloading interpreter..."
					);
					break;
				}

				synchronized (tasks) {
					ScalpelLogger.trace("Runner waiting for notifications.");

					// Extract the oldest pending task from the queue.
					final Task task = tasks.poll();

					// Ensure a task was polled or poll again.
					if (!isEnabled || task == null) {
						// Release the lock and wait for new tasks.
						tasks.wait(1000);
						continue;
					}

					ScalpelLogger.trace("Processing task: " + task.name);
					try {
						// Invoke Python function and get the returned value.
						final Object pythonResult = interp.invoke(
							task.name,
							task.args,
							task.kwargs
						);

						ScalpelLogger.trace("Executed task: " + task.name);

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

					ScalpelLogger.trace("Processed task");

					// Log the result value.
					ScalpelLogger.trace(
						String.valueOf(task.result.orElse("<empty>"))
					);

					task.finished = true;

					synchronized (task) {
						// Wake threads awaiting the task.
						task.notifyAll();

						ScalpelLogger.trace("Notified " + task.name);
					}

					// Sleep the thread while there isn't any new tasks
					tasks.wait(1000);
				}
			}
		} catch (Exception e) {
			// The task loop has crashed, log the stack trace.
			ScalpelLogger.logStackTrace(e);
		}
		// Log the error.
		ScalpelLogger.log("Task loop has crashed");

		isRunnerAlive = false;
		isRunnerStarting = false;

		this.resetChangeIndicators();

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
		this.editorProvider.ifPresent(ScalpelEditorProvider::resetEditors);

		// Return the running thread.
		return thread;
	}

	private Optional<Path> getDefaultIncludePath() {
		final Path defaultVenv = Workspace
			.getDefaultWorkspace()
			.resolve(Workspace.VENV_DIR);

		try {
			return Optional.of(Venv.getSitePackagesPath(defaultVenv));
		} catch (IOException e) {
			ScalpelLogger.warn(
				"Could not find a default include path for JEP (with venv " +
				defaultVenv +
				")"
			);
			ScalpelLogger.error(
				"Could not find a default include path for JEP"
			);
			ScalpelLogger.logStackTrace(e);
		}
		return Optional.empty();
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
					Optional<String> defaultIncludePath = getDefaultIncludePath()
						.map(Path::toString);

					// Instantiate a Python interpreter.
					final SubInterpreter interp = new SubInterpreter(
						new JepConfig()
							.setClassEnquirer(new CustomEnquirer())
							.addIncludePaths(
								defaultIncludePath.orElse(""),
								RessourcesUnpacker.PYTHON_PATH.toString()
							)
					);

					var burpEnv = new HashMap<>(10);

					// Make the Montoya API object accessible in Python
					burpEnv.put("API", API);

					// Set the framework's filename to corresponding Python variable
					// This isn't set by JEP, we have to do it ourselves.
					interp.set("__file__", framework.getAbsolutePath());

					// Set the path to the user script that will define the actual callbacks.
					burpEnv.put(
						"user_script",
						script.orElseThrow().getAbsolutePath()
					);

					burpEnv.put("framework", framework.getAbsolutePath());

					// Pass the selected venv path so it can be activated by the framework.
					burpEnv.put(
						"venv",
						config.getSelectedWorkspacePath() +
						File.separator +
						Workspace.VENV_DIR
					);

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

	public record CallableData(
		String name,
		HashMap<String, String> annotations
	) {}

	@SuppressWarnings({ "unchecked" })
	public List<CallableData> getCallables() throws RuntimeException {
		// TODO: Memoize this
		// Jep doesn't offer any way to list functions, so we have to implement it Python side.
		// Python returns ~ [{"name": <function name>, "annotations": <func.__annotations__>},...]
		return this.safeJepInvoke(Constants.GET_CB_NAME, List.class)
			.map(l -> (List<HashMap<String, Object>>) l)
			.map(List::stream)
			.map(s ->
				s.map(c ->
					new CallableData(
						(String) c.get("name"),
						(HashMap<String, String>) c.get("annotations")
					)
				)
			)
			.map(Stream::toList)
			.orElseThrow(() ->
				new RuntimeException(Constants.GET_CB_NAME + " was not found.")
			);
	}
}
