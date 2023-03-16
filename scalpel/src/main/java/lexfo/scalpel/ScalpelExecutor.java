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
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import jep.ClassEnquirer;
import jep.ClassList;
import jep.Interpreter;
import jep.JepConfig;
import jep.SharedInterpreter;
import lexfo.scalpel.HttpMsgUtils;

public class ScalpelExecutor {

  private class CustomEnquirer implements ClassEnquirer {

    private ClassList base;

    CustomEnquirer() {
      this.base = ClassList.getInstance();
    }

    public String[] getClassNames(String pkg) {
      TraceLogger.log(logger, "getClassNames called with |" + pkg + "|");
      if (pkg == "lexfo") {
        return new String[] { "HttpMsgUtils" };
      }
      return base.getClassNames(pkg);
    }

    public static ClassList getInstance() {
      return getInstance();
    }

    public String[] getSubPackages(String p) {
      TraceLogger.log(logger, "getSubPackages called with |" + p + "|");
      return base.getSubPackages(p);
    }

    public boolean isJavaPackage(String s) {
      TraceLogger.log(logger, "isJavaPackage called with |" + s + "|");

      // https://github.com/ninia/jep/issues/347
      // fucking piece of fucking shit this fucking sucks fuck fuck fuck this FUCK
      if (s.equals("lexfo") | s.equals("lexfo.scalpel")) {
        TraceLogger.log(logger, "Returning true");
        return true;
      }
      return base.isJavaPackage(s);
    }

    public static void main(String[] argv) {
      main(argv);
    }
  }

  private final void printClasses(String pkg) {
    var cl = new CustomEnquirer();
    if (!cl.isJavaPackage(pkg)) {
      TraceLogger.log(logger, pkg + " is not a importable package.");
      return;
    }
    TraceLogger.log(logger, pkg + ":");
    Arrays
      .stream(cl.getClassNames(pkg))
      .forEach(s -> TraceLogger.log(logger, "\t" + s));
  }

  private final void debugClassLoad() {
    // printClasses("burp");
    printClasses("lexfo");
  }

  private class Task {

    String name;
    Object[] args;
    Map<String, Object> kwargs;
    private Optional<Object> result = null;

    public Task(String name, Object[] args, Map<String, Object> kwargs) {
      this.name = name;
      this.args = args;
      this.kwargs = kwargs;

      TraceLogger.log(logger, "Created task: " + name);
    }

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

  private final Logging logger;
  private final MontoyaApi API;
  private File script;
  private Thread runner;
  private Queue<Task> tasks = new LinkedBlockingQueue<>();
  private long lastScriptModificationTimestamp;

  public ScalpelExecutor(MontoyaApi API, Logging logger, String scriptPath) {
    // Store Montoya API object
    this.API = API;

    // Keep a reference to a logger
    this.logger = logger;

    // Create a File wrapper from the script path.
    this.script = new File(scriptPath);

    this.lastScriptModificationTimestamp = this.script.lastModified();

    SharedInterpreter.setConfig(
      new JepConfig().setClassEnquirer(new CustomEnquirer())
    );

    // Launch task thread.
    this.runner = this.launchTaskRunner();
  }

  private final Task addTask(
    String name,
    Object[] args,
    Map<String, Object> kwargs
  ) {
    // Create task object.
    final Task task = new Task(name, args, kwargs);

    // Queue the task.
    tasks.add(task);

    // Return the queued task.
    return task;
  }

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

  private final Boolean hasScriptChanged() {
    // Check if the last modification date has changed since last record.
    final Boolean hasChanged = lastScriptModificationTimestamp != script.lastModified();

    // Update the last modification date record.
    lastScriptModificationTimestamp = script.lastModified();

    // Return the check result.²
    return hasChanged;
  }

  private final SharedInterpreter reloadInterpreter(SharedInterpreter interp) {
    TraceLogger.log(logger, "Reloading interpreter...");
    interp.close();
    return initInterpreter();
  }

  private final Thread launchTaskRunner() {
    // Instantiate the task runner thread.
    final var thread = new Thread(() -> {
      TraceLogger.log(logger, "Starting task loop.");

      try  {
        // Instantiate the interpreter.
        SharedInterpreter interp = initInterpreter();
        while (true) {
          // Relaunch interpreter when file has changed (hot reload).
          if (hasScriptChanged()) {
            TraceLogger.log(logger, script.getPath() + " has changed.");
            interp = reloadInterpreter(interp);
          }
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
          }
        }
      } catch (Exception e) {
        // The task loop has crashed, log the stack trace.
        TraceLogger.logStackTrace(logger, e);
      }
      // Log the error.
      TraceLogger.log(logger, "Task loop has crashed");
    });

    // Start the task runner thread.
    thread.start();

    // Return the running thread.
    return thread;
  }

  private final SharedInterpreter initInterpreter() {
    try {
      // Instantiate a Python interpreter.
      final SharedInterpreter interp = new SharedInterpreter();

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

  // Unused utils function
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

  public void setScript(String path) {
    // Update the script path.
    this.script = new File(path);
  }

  private static final <T extends HttpMessage> String getMessageCbName(T msg) {
    if (
      msg instanceof HttpRequest || msg instanceof HttpRequestToBeSent
    ) return Constants.REQ_CB_NAME;
    if (
      msg instanceof HttpResponse || msg instanceof HttpResponseReceived
    ) return Constants.RES_CB_NAME;
    throw new RuntimeException("Passed wrong type to geMessageCbName");
  }

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

  // Format corresponding callback's Python function name.
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

  public synchronized <T extends Object> Optional<T> safeJepInvoke(
    String name,
    Object[] args,
    Map<String, Object> kwargs,
    Class<T> expectedClass
  ) {
    return awaitTask(name, args, kwargs);
  }

  public <T> Optional<T> safeJepInvoke(
    String name,
    Object arg,
    Class<T> expectedClass
  ) {
    // Call base safeJepInvoke with a single argument and a logger as default kwarg.
    return safeJepInvoke(name, new Object[] { arg }, Map.of(), expectedClass);
  }

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
