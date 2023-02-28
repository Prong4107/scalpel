package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
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

  public HttpRequest callRequestToBeSentCallback(
    HttpRequest req,
    ByteArray text,
    String tab_name
  ) {
    // Create a PyRequest wrapper.
    // TODO: Actually implement the wrapper.
    var pyReq = req;

    // Instantiate interpreter
    // TODO: Handle errors + work with a global interpreter.
    try (Interpreter interp = new SharedInterpreter()) {
      // Run script (declares callbacks)
      interp.runScript(scriptPath);

      // Call request(...) callback
      // https://ninia.github.io/jep/javadoc/4.1/jep/Interpreter.html#invoke-java.lang.String-java.lang.Object...-
      pyReq =
        (HttpRequest) interp.invoke("request", pyReq, text, tab_name, logger);

      // TODO: Extract Burp request.
      var newReq = pyReq;

      // Return new request with debug header
      return newReq.withAddedHeader("X-Scalpel-Request", "true");
    }
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
      pyRes = (HttpResponse) interp.invoke("response", pyRes, logger);

      // TODO: Extract Burp response.
      var newRes = pyRes;

      // Return new request with debug header
      return newRes.withAddedHeader(
        HttpHeader.httpHeader("X-Scalpel-Response", "true")
      );
    }
  }

  private static final String getCallbackName(
    String tabName,
    HttpMessage msg,
    Boolean isInbound
  ) {
    // Format corresponding callback's Python function name.
    var editPrefix = msg instanceof HttpRequest
      ? Constants.REQ_EDIT_PREFIX
      : Constants.RES_EDIT_PREFIX;

    var directionPrefix = isInbound
      ? Constants.IN_PREFIX
      : Constants.OUT_PREFIX;

    var cbName = editPrefix + directionPrefix + tabName;

    return cbName;
  }

  public Optional<ByteArray> callEditorCallback(
    HttpMessage msg,
    Boolean isInbound,
    String tabName
  ) {
    // Get callback's Python function name.
    var cbName = getCallbackName(tabName, msg, isInbound);

    // Instantiate interpreter.
    try (Interpreter interp = new SharedInterpreter()) {
      // Load the script.
      interp.runScript(scriptPath);

      try {
        // Invoke the callback and get it's result.
        var result = interp.invoke(cbName, msg, logger);

        // Empty return when the cb returns None.
        if (result == null) return Optional.empty();

        // Ensure the returned data is a supported type.
        if (result.getClass() != byte[].class) {
          // TODO: Use a "supported type" Set.

          // Log the error in Burp.
          logger.logToError(
            cbName +
            "() returned unsupported type " +
            result.getClass().getName()
          );

          // Empty return.
          return Optional.empty();
        }

        try {
          // Convert the result to Burp's ByteArray class and return it.
          return Optional.of(ByteArray.byteArray((byte[]) result));
        } catch (Exception e) {
          // Something nasty that should be impossible to happen has happened.
          // Log the exception message and stack trace.
          TraceLogger.logExceptionStackTrace(logger, e);
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
}
