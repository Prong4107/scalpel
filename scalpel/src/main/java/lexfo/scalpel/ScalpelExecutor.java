package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import jep.Interpreter;
import jep.SharedInterpreter;

public class ScalpelExecutor {

  private final Logging logger;
  private final MontoyaApi API;

  public ScalpelExecutor(MontoyaApi API, Logging logger) {
    this.API = API;
    this.logger = logger;
  }

  public String[] executeScriptAndCaptureOutput(String scriptContent) {
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
}
