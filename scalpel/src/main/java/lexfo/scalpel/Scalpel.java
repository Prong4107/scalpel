package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import jep.MainInterpreter;

// Burp will auto-detect and load any class that extends BurpExtension.
/**
  The main class of the extension.
  This class is instantiated by Burp Suite and is used to initialize the extension.
*/
public class Scalpel implements BurpExtension {

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

	private static void logConfig(Config config) {
		ScalpelLogger.all("Config:");
		ScalpelLogger.all("Framework: " + config.getFrameworkPath());
		ScalpelLogger.all("Script: " + config.getUserScriptPath());
		ScalpelLogger.all("Venvs: " + config.getVenvPaths());
		ScalpelLogger.all("Default venv: " + Config.getDefaultVenv());
		ScalpelLogger.all("Selected venv: " + config.getSelectedVenv());
	}

	private static void setupJepFromConfig(Config config) throws IOException {
		final String venvPath = config.getOrCreateDefaultVenv(
			config.getFrameworkPath()
		);

		var dir = Venv.getSitePackagesPath(venvPath).toFile();

		final File[] jepDirs = dir.listFiles((current, name) ->
			name.matches("jep")
		);

		if (jepDirs.length == 0) {
			throw new IOException(
				"FATAL: Could not find jep directory in " +
				dir +
				"\nIf the install failed previously, remove the ~/.scalpel directory and reload the extension"
			);
		}

		final String jepDir = jepDirs[0].getAbsolutePath();

		// Adding path to java.library.path is necessary for Windows
		final var oldLibPath = System.getProperty("java.library.path");
		final var newLibPath = jepDir + File.pathSeparator + oldLibPath;
		System.setProperty("java.library.path", newLibPath);

		final String libjepFile = Constants.NATIVE_LIBJEP_FILE;
		final String jepLib = Paths.get(jepDir).resolve(libjepFile).toString();

		ScalpelLogger.all("Loading Jep native library from " + jepLib);
		// Load the library ourselves to catch errors right away.
		System.load(jepLib);
		MainInterpreter.setJepLibraryPath(jepLib);
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
		ScalpelLogger.setLogger(API.logging());

		try {
			ScalpelLogger.all("Initializing...");

			// Extract embeded ressources.
			unpacker = new ScalpelUnpacker();

			ScalpelLogger.all("Extracting ressources...");
			unpacker.initializeResourcesDirectory();

			ScalpelLogger.all("Reading config and initializing venvs...");
			ScalpelLogger.all(
				"(This might take a minute, Scalpel is installing dependencies...)"
			);

			config = new Config(API, unpacker);
			logConfig(config);

			setupJepFromConfig(config);

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
			executor = new ScalpelExecutor(API, unpacker, config);

			// Add the scripting editor tab to Burp UI.
			API
				.userInterface()
				.registerSuiteTab(
					"Scalpel Interpreter",
					UIBuilder.constructScalpelInterpreterTab(config, executor)
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
			ScalpelLogger.all("Initialized scalpel successfully.");
		} catch (Exception e) {
			ScalpelLogger.all(
				"^ An error has occured, look at the \"Errors\" tab ^"
			);
			ScalpelLogger.error("Failed to initialize scalpel:");
			ScalpelLogger.logStackTrace(e);
		}
	}
}
