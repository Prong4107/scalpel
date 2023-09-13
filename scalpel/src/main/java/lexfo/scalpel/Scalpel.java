package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.jediterm.terminal.ui.UIUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import jep.MainInterpreter;

// Burp will auto-detect and load any class that extends BurpExtension.
/**
  The main class of the extension.
  This class is instantiated by Burp Suite and is used to initialize the extension.
*/
public class Scalpel implements BurpExtension {

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
		ScalpelLogger.all(
			"Venvs: " +
			Arrays
				.stream(config.getVenvPaths())
				.collect(Collectors.joining("\",\"", "[\"", "\"]"))
		);
		ScalpelLogger.all("Default venv: " + Workspace.getDefaultWorkspace());
		ScalpelLogger.all(
			"Selected venv: " + config.getSelectedWorkspacePath()
		);
	}

	private static void loadLibPython3() {
		String libPath = executePythonCommand(
			"import sysconfig; print('/'.join([sysconfig.get_config_var('LIBDIR'), 'libpython' + sysconfig.get_config_var('VERSION') + '.dylib']))"
		);

		ScalpelLogger.all("Loading Python library from " + libPath);
		try {
			System.load(libPath);
		} catch (Exception e) {
			throw new RuntimeException(
				"Failed loading" +
				libPath +
				"\nIf you are using an ARM/M1 macOS, make sure you installed the ARM/M1 Burp package and not the Intel one:\n" +
				"https://portswigger.net/burp/releases/professional-community-2023-10-1?requestededition=professional&requestedplatform=macos%20(arm/m1)",
				e
			);
		}
		ScalpelLogger.all("Successfully loaded Python library from " + libPath);
	}

	private static void checkPythonVersion() {
		String version = executePythonCommand(
			"import sys; print('.'.join(map(str, sys.version_info[:3])))"
		);

		if (version != null) {
			String[] versionParts = version.split("\\.");
			int major = Integer.parseInt(versionParts[0]);
			int minor = Integer.parseInt(versionParts[1]);

			if (major < 3 || (major == 3 && minor < 10)) {
				throw new RuntimeException(
					"Detected Python version " +
					version +
					". Requires Python version 3.10 or greater."
				);
			}
		} else {
			throw new RuntimeException("Failed to retrieve Python version.");
		}
	}

	private static String executePythonCommand(String command) {
		try {
			String[] cmd = { "python3", "-c", command };

			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(true); // Redirect stderr to stdout

			Process process = pb.start();
			String output;
			try (
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream())
				)
			) {
				output = reader.readLine();
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new RuntimeException(
					"Python command failed with exit code " + exitCode
				);
			}

			return output;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setupJepFromConfig(Config config) throws IOException {
		final Path venvPath = Workspace
			.getOrCreateDefaultWorkspace(config.getJdkPath())
			.resolve(Workspace.VENV_DIR);

		final var dir = Venv.getSitePackagesPath(venvPath).toFile();

		final File[] jepDirs = dir.listFiles((__, name) -> name.matches("jep"));

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
		final String jepLib = Paths.get(jepDir, libjepFile).toString();

		// Load the library ourselves to catch errors right away.
		ScalpelLogger.all("Loading Jep native library from " + jepLib);
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

			// Ensure we have python >=3.10
			checkPythonVersion();
			if (UIUtil.isMac) {
				// It may be required to manually load libpython on MacOS for jep not to break
				// https://github.com/ninia/jep/issues/432#issuecomment-1317590878
				loadLibPython3();
			}

			// Extract embeded ressources.
			ScalpelLogger.all("Extracting ressources...");
			RessourcesUnpacker.extractRessourcesToHome();

			ScalpelLogger.all("Reading config and initializing venvs...");
			ScalpelLogger.all(
				"(This might take a minute, Scalpel is installing dependencies...)"
			);

			config = new Config(API);
			logConfig(config);

			setupJepFromConfig(config);

			// Initialize Python task queue.
			executor = new ScalpelExecutor(API, config);

			// Add the configuration tab to Burp UI.
			API
				.userInterface()
				.registerSuiteTab(
					"Scalpel",
					UIBuilder.constructConfigTab(
						API,
						executor,
						config,
						API.userInterface().currentTheme()
					)
				);

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

			// Burp race-condition: cannot unload an extension while in initialize()
			Async.run(() -> {
				try {
					Thread.sleep(500);
				} catch (InterruptedException __) {}

				API.extension().unload();
			});
		}
	}
}
