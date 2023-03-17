package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
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
      TraceLogger.log(logger, "getClassNames called with |" + pkg + "|");
      return base.getClassNames(pkg);
    }

    /**
     * Gets the names of all the sub-packages of a package.
     *
     * @param p the name of the package.
     * @return an array of the names of the sub-packages of the package.
     */
    public String[] getSubPackages(String p) {
      TraceLogger.log(logger, "getSubPackages called with |" + p + "|");
      return base.getSubPackages(p);
    }

    /**
     * Determines whether a string represents a valid Java package.
     *
     * @param s the string to check.
     * @return true if the string represents a valid Java package, false otherwise.
     */
    public boolean isJavaPackage(String s) {
      TraceLogger.log(logger, "isJavaPackage called with |" + s + "|");

      // https://github.com/ninia/jep/issues/347
      // fucking piece of fucking shit this fucking sucks fuck fuck fuck this FUCK
      if (s.equals("lexfo") || s.equals("lexfo.scalpel")) {
        TraceLogger.log(logger, "Returning true");
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
    String name;

    /**
     * The arguments passed to the task.
     */
    Object[] args;

    /**
     * The keyword arguments passed to the task.
     */
    Map<String, Object> kwargs;

    /**
     * An optional object containing the result of the task, if it has been completed.
     */
    private Optional<Object> result = null;

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

      TraceLogger.log(logger, "Created task: " + name);
    }

    /**
     * Add the task to the queue and wait for it to be completed by the task thread.
     *
     * @return the result of the task.
     */
    public Optional<Object> awaitResult() {
      // Acquire the lock on the Task object.
      synchronized (this) {
        // Ensure we return only when result has been set
        // (apprently wait() might return even if notify hasn't been called for some weird software and hardware issues)
        while (result == null) {
          // Wrap the wait in try/catch to handle InterruptedException.
          try {
            // Wait for the object to be notified.
            this.wait();
          } catch (InterruptedException e) {
            // Log the error.
            TraceLogger.logError(logger, "Task " + name + "interrupted:");

            // Log the stack trace.
            TraceLogger.logStackTrace(logger, e);
          }
        }
      }

      // Return the awaited result.
      return result;
    }
  }

  /**
   * The logging object to use for logging messages.
   */
  private final Logging logger;

  /**
   * The MontoyaApi object to use for sending and receiving HTTP messages.
   */
  private final MontoyaApi API;

  /**
   * The path of the Scalpel script to execute.
   */
  private File script;

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
  private long lastScriptModificationTimestamp;

  /**
   * Flag indicating whether the task runner loop is running.
   */
  private Boolean isRunnerAlive = false;

  /**
   * Constructs a new ScalpelExecutor object.
   *
   * @param API the MontoyaApi object to use for sending and receiving HTTP messages.
   * @param logger the Logging object to use for logging messages.
   * @param scriptPath the path of the Scalpel script to execute.
   */

  public ScalpelExecutor(MontoyaApi API, Logging logger, String scriptPath) {
    // Store Montoya API object
    this.API = API;

    // Keep a reference to a logger
    this.logger = logger;

    // Create a File wrapper from the script path.
    this.script = new File(scriptPath);

    this.lastScriptModificationTimestamp = this.script.lastModified();

    // Launch task thread.
    this.runner = this.launchTaskRunner();
  }

  /**
   * Adds a new task to the queue of tasks to be executed by the script.
   *
   * @param name the name of the python function to be called.
   * @param args the arguments to pass to the python function.
   * @param kwargs the keyword arguments to pass to the python function.
   * @return a Task object representing the added task.
   */
  private final Task addTask(
    String name,
    Object[] args,
    Map<String, Object> kwargs
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
      } else {
        // The runner is dead, reject this task to avoid blocking Burp when awaiting.
        task.result = Optional.empty();
      }
    }

    // Return the queued or rejected task.
    return task;
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
    Map<String, Object> kwargs
  ) {
    // Log before awaiting to debug potential deadlocks.
    System.out.println("Awaiting task: " + name);
    try {
      // Queue a new task and await it's result.
      Object result = addTask(name, args, kwargs).awaitResult();

      // Ensure the result isn't null (happens if await crashed.)
      if (result == null) return Optional.empty();

      // Cast the result to the expected type.
      var castedResult = (Optional<T>) result;

      // Log that the task has been successfully awaited.
      System.out.println("Finished awaiting task: " + name);

      // Return the well-formed result.
      return castedResult;
    } catch (Exception e) {
      // Log the error stack trace.
      TraceLogger.logStackTrace(logger, e);
    }
    // Log the failure.
    logger.logToError("Failed awaiting task: " + name);

    // Return an empty object.
    return Optional.empty();
  }

  /**
   * Checks if the script file has been modified since the last check.
   *
   * @return true if the script file has been modified since the last check, false otherwise.
   */
  private final Boolean hasScriptChanged() {
    // Check if the last modification date has changed since last record.
    final Boolean hasChanged =
      lastScriptModificationTimestamp != script.lastModified();

    // Update the last modification date record.
    lastScriptModificationTimestamp = script.lastModified();

    // Return the check result.²
    return hasChanged;
  }

  /**
   * Close and reinstantiante the interpreter and returns it.
   *
   * @param interp the interpreter to reload.
   * @return the reloaded interpreter.
   */
  private final SubInterpreter reloadInterpreter(SubInterpreter interp) {
    TraceLogger.log(logger, "Reloading interpreter...");
    interp.close();
    return initInterpreter();
  }

  /**
   * Launches the task runner thread.
   *
   * @return the launched thread.
   */
  private final Thread launchTaskRunner() {
    // Instantiate the task runner thread.
    final var thread = new Thread(() -> {
      TraceLogger.log(logger, "Starting task loop.");

      try {
        // Instantiate the interpreter.
        SubInterpreter interp = initInterpreter();
        isRunnerAlive = true;
        while (true) {
          // Relaunch interpreter when file has changed (hot reload).
          if (hasScriptChanged()) {
            TraceLogger.log(logger, script.getPath() + " has changed.");
            interp = reloadInterpreter(interp);
          }
          synchronized (tasks) {
            TraceLogger.log(logger, "Runner waiting for notifications.");

            // Sleep the thread while there isn't any new tasks
            tasks.wait();

            // Extract the oldest pending task from the queue.
            final Task task = tasks.poll();

            // Ensure a task was polled or poll again.
            if (task == null) continue;

            synchronized (task) {
              // Log that a task was polled.
              TraceLogger.log(logger, "Processing task: " + task.name);

              // Initialize the task result.
              task.result = Optional.empty();
              try {
                // Invoke python function and get the returned value.
                final var pythonResult = interp.invoke(
                  task.name,
                  task.args,
                  task.kwargs
                );

                // Log the success.
                TraceLogger.log(logger, "Executed task: " + task.name);

                // Let the result value to an empty optional when nothing is returned.
                if (pythonResult != null) {
                  // Wrap the returned value in an Optional.
                  task.result = Optional.of(pythonResult);
                }
              } catch (Exception e) {
                // Log the failure.
                TraceLogger.logError(logger, "Error in task loop:");

                // Log the error.
                TraceLogger.logStackTrace(logger, e);
              }
              // Log the success.
              TraceLogger.log(logger, "Processed task");

              // Log the result value.
              TraceLogger.log(logger, "" + task.result.orElse("null"));

              // Notify the task to release the awaitResult() wait() lock.
              task.notifyAll();

              TraceLogger.log(logger, "Notified " + task.name);
            }
          }
        }
      } catch (Exception e) {
        // The task loop has crashed, log the stack trace.
        TraceLogger.logStackTrace(logger, e);
      }
      // Log the error.
      TraceLogger.log(logger, "Task loop has crashed");

      synchronized (tasks) {
        tasks.forEach(t -> {
          synchronized (t) {
            t.result = Optional.empty();
            t.notifyAll();
          }

          tasks.clear();
        });

        isRunnerAlive = false;
      }

      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        TraceLogger.logStackTrace(logger, e);
      }

      // Relaunch the task thread
      this.runner = launchTaskRunner();
    });

    // Start the task runner thread.
    thread.start();

    // Return the running thread.
    return thread;
  }

  /**
   * Initializes the interpreter.
   *
   * @return the initialized interpreter.
   */
  private final SubInterpreter initInterpreter() {
    try {
      // Instantiate a Python interpreter.
      SubInterpreter interp = new SubInterpreter(
        new JepConfig().setClassEnquirer(new CustomEnquirer())
      );

      // Make the Montoya API object accessible in Python
      interp.set("__montoya__", API);

      // Set the script's filename to corresponding Python variable
      interp.set("__file__", script.getAbsolutePath());

      // Set the script's directory to be able to add it to Python's path.
      interp.set("__directory__", script.getParent());

      // Add logger global
      interp.set("__logger__", logger);

      // Add the script's directory to Python's path to allow imports of adjacent files.
      interp.exec(
        """
    from sys import path
    path.append(__directory__)
    """
      );

      // Set importable logger.
      interp.exec(
        """
    import pyscalpel._globals
    pyscalpel._globals.logger = __logger__
    """
      );

      // Run the script.
      interp.runScript(script.getAbsolutePath());

      // Return the initialized interpreter.
      return interp;
    } catch (Exception e) {
      logger.logToError("Failed to instantiate interpreter:");
      TraceLogger.logStackTrace(logger, e);
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
      interp.exec(scriptContent);
      interp.exec("captured_out = temp_out.getvalue()");
      interp.exec("captured_err = temp_err.getvalue()");

      String capturedOut = (String) interp.getValue("captured_out");
      String capturedErr = (String) interp.getValue("captured_err");
      logger.logToOutput(
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
   * Sets the script path.
   *
   * @param path the path to the script.
   */
  public void setScript(String path) {
    // Update the script path.
    this.script = new File(path);
  }

  /**
   * Returns the name of the corresponding Python callback for the given message intercepted by Proxy.
   *
   * @param <T> the type of the message.
   * @param msg the message to get the callback name for.
   * @return the name of the corresponding Python callback.
   */
  private static final <T extends HttpMessage> String getMessageCbName(T msg) {
    if (
      msg instanceof HttpRequest || msg instanceof HttpRequestToBeSent
    ) return Constants.REQ_CB_NAME;
    if (
      msg instanceof HttpResponse || msg instanceof HttpResponseReceived
    ) return Constants.RES_CB_NAME;
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
  public <T extends HttpMessage> Optional<T> callIntercepterCallback(T msg) {
    // Call the corresponding Python callback and add a debug HTTP header.
    return safeJepInvoke(getMessageCbName(msg), msg, (Class<T>) msg.getClass())
      .flatMap(r ->
        Optional.of(
          HttpMsgUtils.updateHeader(
            r,
            "X-Scalpel-" + HttpMsgUtils.getClassName(msg),
            "true"
          )
        )
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
    String tabName,
    Boolean isRequest,
    Boolean isInbound
  ) {
    // Either req_ or res_ depending if it is a request or a response.
    final var editPrefix = isRequest
      ? Constants.REQ_EDIT_PREFIX
      : Constants.RES_EDIT_PREFIX;

    // Either in_ or out_ depending on context.
    final var directionPrefix = isInbound
      ? Constants.IN_PREFIX
      : Constants.OUT_PREFIX;

    // Concatenate the prefixes and the tab name.
    final var cbName = editPrefix + directionPrefix + tabName;

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
    return awaitTask(name, args, kwargs);
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
    return safeJepInvoke(name, new Object[] { arg }, Map.of(), expectedClass);
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
    // Call safeJepInvoke with the corresponding function name and a logger as a default kwarg.
    return safeJepInvoke(
      getEditorCallbackName(tabName, isRequest, isInbound),
      params,
      Map.of(),
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
    Boolean isRequest,
    Boolean isInbound,
    String tabName,
    Class<T> expectedClass
  ) {
    // Call base method with a single parameter.
    return callEditorCallback(
      new Object[] { param },
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
    Boolean isInbound,
    String tabName
  ) {
    // Pass a request to py. callback and convert the returned byte[] to Burp's ByteArray.
    // When no data is returned by the callback, an empty Optional is returned.
    return callEditorCallback(
      msg,
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
  public Optional<ByteArray> callEditorCallback(
    HttpMessage msg,
    ByteArray byteArray,
    Boolean isInbound,
    String tabName
  ) {
    // Pass a request to py. callback and convert the returned byte[] to Burp's ByteArray.
    // When no data is returned by the callback, an empty Optional is returned.
    return callEditorCallback(
      new Object[] { msg, byteArray.getBytes() },
      msg instanceof HttpRequest,
      isInbound,
      tabName,
      byte[].class
    )
      .flatMap(bytes -> Optional.of(ByteArray.byteArray(bytes)));
  }
}
