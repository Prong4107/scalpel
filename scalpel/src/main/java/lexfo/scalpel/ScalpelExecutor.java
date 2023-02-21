package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
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
    // TODO: Create a PyRequest / PyResponse wrapper.
    var pyReq = req;

    // Instantiate interpreter
    // TODO: Handle errors
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
      return newReq.withAddedHeader(HttpHeader.httpHeader("X-Scalpel", "true"));
    }
  }
}
