package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import jep.MainInterpreter;

// Burp will auto-detect and load any class that extends BurpExtension.
/**
  The main class of the extension.
  This class is instantiated by Burp Suite and is used to initialize the extension.
*/
public class Scalpel implements BurpExtension {

	/**
	 * The logger object used to log messages to Burp Suite's output tab.
	 */
	private Logging logger;

	/**
	 * The ScalpelUnpacker object used to extract the extension's resources to a temporary directory.
	 */
	private ScalpelUnpacker unpacker;

	/**
	 * The ScalpelExecutor object used to execute Python scripts.
	 */
	private ScalpelExecutor executor;

	/**
	 * The MontoyaApi object used to interact with Burp Suite.
	 */
	private MontoyaApi API;

	private Config config;

	/**
     * Initializes the extension.
    @param API The MontoyaApi object to use.
	*/
	@Override
	public void initialize(MontoyaApi API) {
		this.API = API;

		// Set displayed extension name.
		API.extension().setName("Lexfo Scalpel extension");

		// Create a logger that will display messages in Burp extension logs.
		// TODO: Replace with TraceLogger that also logs to standard output streams.
		logger = API.logging();

		try {
			TraceLogger.log(logger, "Initializing...");

			// Extract embeded ressources.
			unpacker = new ScalpelUnpacker(logger);
			unpacker.initializeResourcesDirectory();

			// Read config files.
			config = new Config(API, unpacker);

			// Add the scripting editor tab to Burp UI.
			API
				.userInterface()
				.registerSuiteTab(
					"Scalpel Interpreter",
					UIBuilder.constructScalpelInterpreterTab(
						config,
						executor,
						logger
					)
				);

			// Add the configuration tab to Burp UI.
			API
				.userInterface()
				.registerSuiteTab(
					"Scalpel",
					UIBuilder.constructConfigTab(
						executor,
						config,
						API.userInterface().currentTheme()
					)
				);

			// Initialize Python task queue.
			executor = new ScalpelExecutor(API, unpacker, logger, config);

			// Create the provider responsible for creating the request/response editors for Burp.
			final var provider = new ScalpelEditorProvider(API, executor);

			// Inject dependency to solve circular dependency.
			executor.setEditorsProvider(provider);

			// Add editor tabs to Burp
			API.userInterface().registerHttpRequestEditorProvider(provider);
			API.userInterface().registerHttpResponseEditorProvider(provider);

			// Intercept HTTP requests
			API
				.http()
				.registerHttpHandler(
					new ScalpelHttpRequestHandler(API, provider, executor)
				);

			// Extension is fully loaded.
			TraceLogger.log(logger, "Initialized scalpel successfully.");
		} catch (Exception e) {
			TraceLogger.logError(logger, "Failed to initialize scalpel:");
			TraceLogger.logStackTrace(logger, e);
		}
	}
}
