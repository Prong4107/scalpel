package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.management.RuntimeErrorException;
import jep.MainInterpreter;
import lexfo.scalpel.TraceLogger.Level;

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

	private static void logConfig(Logging logger, Config config) {
		var level = Level.ALL;
		TraceLogger.log(logger, level, "Config:");
		TraceLogger.log(
			logger,
			level,
			"Framework: " + config.getFrameworkPath()
		);
		TraceLogger.log(logger, level, "Script: " + config.getUserScriptPath());
		TraceLogger.log(logger, level, "Venvs: " + config.getVenvPaths());
		TraceLogger.log(
			logger,
			level,
			"Default venv: " + Config.getDefaultVenv()
		);
		TraceLogger.log(
			logger,
			level,
			"Selected venv: " + config.getSelectedVenvPath()
		);
	}

	private static void setupJepFromConfig(Logging logger, Config config) {
		final String venvPath = Config.getDefaultVenv();
		final File directory = new File(venvPath + "/lib/");
		final File[] directories = directory.listFiles((current, name) ->
			new File(current, name).isDirectory()
		);

		final var pythonDir = Arrays
			.stream(directories)
			.filter(d -> d.getName().startsWith("python3"))
			.findAny();

		final var sitePackagesDirs = pythonDir.map(d ->
			d.listFiles((current, name) -> name.matches("site-packages"))
		);

		sitePackagesDirs.ifPresentOrElse(
			dirs -> {
				if (dirs.length == 0) {
					throw new RuntimeException(
						"FATAL: Could not find Jep directory"
					);
				}

				final File[] jepDirs =
					dirs[0].listFiles((current, name) -> name.matches("jep"));

				if (jepDirs.length == 0) {
					throw new RuntimeException(
						"FATAL: Could not find jep directory in " + dirs[0]
					);
				}

				final String jepDir = jepDirs[0].getAbsolutePath();
				// TODO: Windows
				final String jepLib = Paths
					.get(jepDir)
					.resolve("libjep.so")
					.toString();

				TraceLogger.log(
					logger,
					Level.ALL,
					"Loading Jep native library from " + venvPath
				);
				MainInterpreter.setJepLibraryPath(jepLib);
			},
			() -> {
				throw new RuntimeException(
					"FATAL: Could not find Jep site-packages directory"
				);
			}
		);
	}

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
		logger = API.logging();

		try {
			TraceLogger.log(logger, Level.ALL, "Initializing...");

			// Extract embeded ressources.
			unpacker = new ScalpelUnpacker(logger);
			unpacker.initializeResourcesDirectory();

			TraceLogger.log(logger, Level.ALL, "Reading config...");
			// Read config files and init the default venv.
			config = new Config(API, unpacker);
			logConfig(logger, config);

			setupJepFromConfig(logger, config);

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
			TraceLogger.log(
				logger,
				Level.ALL,
				"Initialized scalpel successfully."
			);
		} catch (Exception e) {
			TraceLogger.logError(logger, "Failed to initialize scalpel:");
			TraceLogger.logStackTrace(logger, e);
		}
	}
}
