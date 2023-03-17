package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import jep.MainInterpreter;

//Burp will auto-detect and load any class that extends BurpExtension.
/**
  The main class of the extension.
  This class is instantiated by Burp Suite and is used to initialize the extension.
*/
public class Scalpel implements BurpExtension {

  /**
    The logger object used to log messages to Burp Suite's output tab.
  */
  private Logging logger;

  /**
    The ScalpelUnpacker object used to extract the extension's resources to a temporary directory.
  */
  private ScalpelUnpacker unpacker;

  /**
    The ScalpelExecutor object used to execute Python scripts.
  */
  private ScalpelExecutor executor;

  /**
    The MontoyaApi object used to interact with Burp Suite.
  */
  private MontoyaApi API;

  /**
    Initializes the extension.
    @param API The MontoyaApi object to use.
  */
  @Override
  public void initialize(MontoyaApi API) {
    // Init API member.
    this.API = API;

    // Set extension name.
    API.extension().setName("Lexfo Scalpel extension");

    // Init logger member.
    logger = API.logging();

    // Show that the extension is loading.
    logger.logToOutput("Initializing...");

    // Change this to stop Python from being initialized (for JEP debugging purposes)
    Boolean initPython = true;

    // Init JEP stuff.
    if (initPython) {
      // Instantiate unpacker
      unpacker = new ScalpelUnpacker(logger);

      // Extract the ressources to a new unique temporary directory.
      unpacker.initializeResourcesDirectory();

      // Set Jep path.
      MainInterpreter.setJepLibraryPath(
        unpacker.getResourcesPath() + "/libjep.so"
      );

      // Instantiate the executor (handles Python execution)
      executor =
        new ScalpelExecutor(
          API,
          logger,
          // TODO: CHANGE ME!
          "/home/nol/Desktop/piperpp/scalpel/scripts/editorTest.py"
        );

      // Add the scripting editor tab.
      API
        .userInterface()
        .registerSuiteTab(
          "Scalpel Interpreter",
          UIBuilder.constructScalpelInterpreterTab(executor, logger)
        );

      // Add request editor tab
      var provider = new ScalpelEditorProvider(API, executor);

      // Add the request editor to Burp.
      API.userInterface().registerHttpRequestEditorProvider(provider);

      // Add the response editor to Burp.
      API.userInterface().registerHttpResponseEditorProvider(provider);

      // Add an HTTP intercepter to Burp.
      API
        .http()
        .registerHttpHandler(
          new ScalpelHttpRequestHandler(API, provider, executor)
        );
    }

    // Log that the extension has finished loading.
    logger.logToOutput("Initialized successfully.");
  }
}
