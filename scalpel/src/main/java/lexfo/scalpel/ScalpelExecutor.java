package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import jep.Interpreter;
import jep.SharedInterpreter;

public class ScalpelExecutor {

  private final Logging logger;
  private final MontoyaApi API;
  private String scriptPath;

  public ScalpelExecutor(MontoyaApi API, Logging logger, String scriptPath) {
    this.API = API;
    this.logger = logger;
    this.scriptPath = scriptPath;
  }

  public String[] evalAndCaptureOutput(String scriptContent) {
    try (Interpreter interp = new SharedInterpreter()) {
      // Running Python instructions on the fly.
      // https://github.com/t2y/jep-samples/blob/master/src/HelloWorld.java

      interp.set("montoyaAPI", API);
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
    this.scriptPath = path;
  }

  public Optional<HttpRequest> callRequestToBeSentCallback(HttpRequest req) {
    // Create a PyRequest wrapper.
    // TODO: Actually implement the wrapper.
    var pyReq = req;

    return safeJepInvoke(Constants.REQ_CB_NAME, pyReq, HttpRequest.class)
      .flatMap(r -> Optional.of(r.withAddedHeader("X-Scalpel-Request", "true"))
      );
  }

  public HttpResponse callResponseReceivedCallback(HttpResponse res) {
    // Create a PyResponse wrapper.
    // TODO: Actually implement the wrapper.
    var pyRes = res;

    // Instantiate interpreter
    // TODO: Handle errors + work with a global interpreter.
    try (Interpreter interp = new SharedInterpreter()) {
      // Run script (declares callbacks)
      interp.runScript(scriptPath);

      // Call response(...) callback
      // https://ninia.github.io/jep/javadoc/4.1/jep/Interpreter.html#invoke-java.lang.String-java.lang.Object...-
      pyRes =
        (HttpResponse) interp.invoke(
          "response",
          new Object[] { pyRes },
          Map.of("logger", logger)
        );

      // TODO: Extract Burp response.
      var newRes = pyRes;

      // Return new request with debug header
      return newRes.withAddedHeader(
        HttpHeader.httpHeader("X-Scalpel-Response", "true")
      );
    }
  }

  private static final String getEditorCallbackName(
    String tabName,
    Boolean isRequest,
    Boolean isInbound
  ) {
    // Format corresponding callback's Python function name.
    var editPrefix = isRequest
      ? Constants.REQ_EDIT_PREFIX
      : Constants.RES_EDIT_PREFIX;

    var directionPrefix = isInbound
      ? Constants.IN_PREFIX
      : Constants.OUT_PREFIX;

    var cbName = editPrefix + directionPrefix + tabName;

    return cbName;
  }

  @SuppressWarnings("unchecked")
  public <T extends Object> Optional<T> safeJepInvoke(
    String name,
    Object[] args,
    Map<String, Object> kwargs,
    Class<T> expectedClass
  ) {
    // Instantiate interpreter.
    try (Interpreter interp = new SharedInterpreter()) {
      // Load the script.
      // TODO: Load at construct and re-use interpreter
      interp.runScript(scriptPath);

      try {
        // Invoke the callback and get it's result.
        var result = interp.invoke(name, args, kwargs);

        // Empty return when the cb returns None.
        if (result == null) return Optional.empty();

        // Ensure the returned data is a supported type.
        try {
          // Convert the result to provided expected class and return it.s
          return Optional.of(((T) result));
        } catch (Exception e) {
          // Log the error in Burp.
          logger.logToError(
            name +
            "() returned unexpected type " +
            result.getClass().getSimpleName() +
            " instead of " +
            expectedClass.getSimpleName() +
            " | param: " +
            args.getClass().getSimpleName()
          );

          // Log the stack trace.
          TraceLogger.logStackTrace(logger, true);

          // Empty return.
          return Optional.empty();
        }
      } catch (Exception e) {
        // There has been an error in the callback invokation
        // Log the exception message and stack trace.
        TraceLogger.logExceptionStackTrace(logger, e);
      }
    } catch (Exception e) {
      // There has been an error in the interpreter instantiation or script evaluation.
      // Log the exception message and stack trace.
      TraceLogger.logExceptionStackTrace(logger, e);
    }

    // There has been an error, so return an empty Optional.
    return Optional.empty();
  }

  public <T extends Object> Optional<T> safeJepInvoke(
    String name,
    Object arg,
    Class<T> expectedClass
  ) {
    return safeJepInvoke(
      name,
      new Object[] { arg },
      Map.of("logger", logger),
      expectedClass
    );
  }

  @SuppressWarnings("unchecked")
  public <T extends Object> Optional<T> callEditorCallback(
    Object[] params,
    Boolean isRequest,
    Boolean isInbound,
    String tabName,
    Class<T> expectedClass
  ) {
    return safeJepInvoke(
      getEditorCallbackName(tabName, isRequest, isInbound),
      params,
      Map.of("logger", logger),
      expectedClass
    );
  }

  public <T extends Object> Optional<T> callEditorCallback(
    Object param,
    Boolean isRequest,
    Boolean isInbound,
    String tabName,
    Class<T> expectedClass
  ) {
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
