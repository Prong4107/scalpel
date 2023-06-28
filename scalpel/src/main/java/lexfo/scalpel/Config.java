package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import javax.swing.JOptionPane;

/**
 * Scalpel configuration.
 *
 *
 * 	By default, the project configuration file is located in the $HOME/.scalpel directory.
 *
 *	The file name is the project id with the .json extension.
 *	The project ID is an UUID stored in the extension data:
 *	https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/persistence/Persistence.html#extensionData()
 *
 *	The configuration file looks something like this:
 *	{
 *		"venvPaths": [
 *			"/path/to/venv1",
 *			"/path/to/venv2"
 *		],
 *		"scriptPath": "/path/to/script.py",
 *		"frameworkPath": "/path/to/framework"
 *	}
 *
 *	The file is not really designed to be directly edited by the user, but rather by the extension itself.
 *
 *	A configuration file is needed because we need to store global persistent data arrays. (e.g. venvPaths)
 *	Which can't be done with the Java Preferences API.
 *	Furthermore, it's simply more convenient to store as JSON and we already have a scalpel directory to store 'ad-hoc' python venvs.
 */
public class Config {

	/**
	 * Global configuration.
	 *
	 * This is the configuration that is shared between all projects.
	 * It contains the list of venvs and the default values.
	 *
	 * The default values are inferred from the user behavior.
	 * For a new project, the default venv, script and framework paths are laste ones selected by the user in any different project.
	 * If the user has never selected a venv, script or framework, the default values are set to default values.
	 */
	private static class _GlobalData {

		/**
		 * List of registered venv paths.
		 */
		public ArrayList<String> venvPaths = new ArrayList<String>();
		public String defaultVenvPath = "";
		public String defaultScriptPath = "";
		public String defaultFrameworkPath = "";
	}

	// Persistent data for a specific project.
	private static class _ProjectData {

		/*
		 * The venv to run the script in.
		 */
		public String venvPath = "";

		/*
		 * The script to run.
		 */
		public String userScriptPath = "";

		/*
		 * The framework to use.
		 */
		public String frameworkPath = "";
	}

	private final _GlobalData globalConfig;
	private final _ProjectData projectConfig;
	private long lastModified = System.currentTimeMillis();

	// Scalpel configuration directory basename
	private static final String CONFIG_DIR = ".scalpel";

	// Scalpel configuration file extension
	private static final String CONFIG_EXT = ".json";

	// UUID generated to identify the project (because the project name cannot be fetched)
	// This is stored in the extension data which is specific to the project.
	private final String projectID;

	// Path to the project configuration file
	private final File projectScalpelConfig;

	// Prefix for the extension data keys
	private static final String DATA_PREFIX = "scalpel.";

	// Key for the project ID
	private static final String DATA_PROJECT_ID_KEY = DATA_PREFIX + "projectID";

	// Venv that will be created and used when none exists
	private static final String DEFAULT_VENV_NAME = "default";

	public Config(MontoyaApi API, ScalpelUnpacker unpacker) {
		// Get the extension data to store and get the project ID back.
		PersistedObject extensionData = API.persistence().extensionData();

		// Get the project ID from the extension data or generate a new one when it doesn't exist.
		this.projectID =
			Optional
				.ofNullable(extensionData.getString(DATA_PROJECT_ID_KEY))
				.orElseGet(() -> {
					String id = java.util.UUID.randomUUID().toString();
					extensionData.setString(DATA_PROJECT_ID_KEY, id);
					return id;
				});

		// Set the path to the project configuration file
		this.projectScalpelConfig =
			new File(getScalpelDir(), projectID + CONFIG_EXT);

		// Load global config
		File globalConfigFile = getGlobalConfigFile();

		// Load global config or create a new one on failure. (e.g. file doesn't exist)
		globalConfig =
			Optional
				.of(globalConfigFile)
				.filter(File::exists)
				.map(file -> IO.readJSON(file, _GlobalData.class))
				.map(d -> {
					// Remove venvs that were deleted by an external process.
					d.venvPaths.removeIf(path -> !new File(path).exists());

					// Ensure that there is at least one venv.
					if (d.venvPaths.size() == 0) {
						d.venvPaths.add(getOrCreateDefaultVenv());
					}

					// Select the first venv if the default one doesn't exist anymore or if it's not set.
					d.defaultVenvPath =
						Optional
							.ofNullable(d.defaultVenvPath)
							.filter(path -> new File(path).exists())
							.orElseGet(() -> d.venvPaths.get(0));
					return d;
				})
				.orElseGet(() -> getDefaultGlobalData(unpacker));

		// Load project config or create a new one on failure. (e.g. file doesn't exist)
		projectConfig =
			Optional
				.of(projectScalpelConfig)
				.filter(File::exists)
				.map(file -> IO.readJSON(file, _ProjectData.class))
				.map(d -> {
					d.venvPath =
						Optional
							.ofNullable(d.venvPath) // Ensure the venv path is set.
							.filter(p -> globalConfig.venvPaths.contains(p)) // Ensure the selected venv is registered.
							.orElse(globalConfig.defaultVenvPath); // Otherwise, use the default venv.
					return d;
				})
				.orElseGet(this::getDefaultProjectData);

		// Write the global config to the file if they didn't exist.
		saveAllConfig();
	}

	/**
	 * Write the global configuration to the global configuration file.
	 */
	private synchronized void saveGlobalConfig() {
		IO.writeJSON(getGlobalConfigFile(), globalConfig);
	}

	/**
	 * Write the project configuration to the project configuration file.
	 */
	private synchronized void saveProjectConfig() {
		this.lastModified = System.currentTimeMillis();
		IO.writeJSON(projectScalpelConfig, projectConfig);
	}

	/**
	 * Write the global and project configuration to their respective files.
	 */
	private synchronized void saveAllConfig() {
		saveGlobalConfig();
		saveProjectConfig();
	}

	/**
	 * Get the last modification time of the project configuration file.
	 *
	 * This is used to reload the execution configuration when the project configuration file is modified.
	 * @return The last modification time of the project configuration file.
	 */
	public long getLastModified() {
		return lastModified;
	}

	public static String getDefaultVenv() {
		return Paths
			.get(getDefaultVenvsDir().getAbsolutePath())
			.resolve(DEFAULT_VENV_NAME)
			.toString();
	}

	/**
	 * Get the scalpel configuration directory.
	 *
	 * @return The scalpel configuration directory. (default: $HOME/.scalpel)
	 */
	public static File getScalpelDir() {
		final Path home = new File(System.getProperty("user.home")).toPath();

		final File dir = new File(home.toFile(), CONFIG_DIR);
		if (!dir.exists()) {
			dir.mkdir();
		}

		return dir;
	}

	/**
	 * Get the default venvs directory.
	 *
	 * @return The default venvs directory. (default: $HOME/.scalpel/venvs)
	 */
	public static File getDefaultVenvsDir() {
		final File dir = new File(getScalpelDir(), "venvs");
		if (!dir.exists()) {
			dir.mkdir();
		}

		return dir;
	}

	/**
	 * Get the global configuration file.
	 *
	 * @return The global configuration file. (default: $HOME/.scalpel/global.json)
	 */
	public static File getGlobalConfigFile() {
		return new File(getScalpelDir(), "global" + CONFIG_EXT);
	}

	private static String getJavaHome() throws IOException {
		// Cannot do this because Burp comes and runs with it's own JRE
		//	> return System.getProperty("java.home");
		// We try to find the JDK form the javac binary path

		final String binaryName = "javac";
		final String binaryPath = findBinaryInPath(binaryName);

		if (binaryPath != null) {
			final Path absolutePath = Paths.get(binaryPath).toRealPath();
			final Path parentDir = absolutePath.getParent().getParent();
			return parentDir.toString();
		}
		return null;
	}

	private static String findBinaryInPath(String binaryName) {
		final String systemPath = System.getenv("PATH");
		final String[] pathDirs = systemPath.split(
			System.getProperty("path.separator")
		);

		for (String pathDir : pathDirs) {
			final Path path = Paths.get(pathDir, binaryName);
			if (Files.exists(path)) {
				return path.toString();
			}
		}
		return null;
	}

	/**
	 * Get the default venv path.
	 * This is the venv that will be used when the project is created.
	 * If the default venv does not exist, it will be created.
	 * If the default venv cannot be created, an exception will be thrown.
	 *
	 * @return The default venv path.
	 */
	public static String getOrCreateDefaultVenv() {
		final File defaultPath = new File(
			getDefaultVenvsDir(),
			DEFAULT_VENV_NAME
		);
		final String path = defaultPath.getAbsolutePath();

		if (!defaultPath.exists()) {
			defaultPath.mkdir();
		} else if (!defaultPath.isDirectory()) {
			throw new RuntimeException("Default venv path is not a directory");
		} else {
			return path;
		}

		try {
			if (Venv.create(path) != 0) {
				throw new RuntimeException(
					"Failed to create default venv\n" +
					"Ensure that pip and openjdk-17 are installed and javac in in PATH."
				);
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		try {
			final int res = Venv.install(
				path,
				Map.of("JAVA_HOME", getJavaHome()),
				"jep",
				"requests",
				"requests-toolbelt",
				"mitmproxy"
			);
			if (res != 0) {
				throw new RuntimeException(
					"Could not not install dependencies."
				);
			}
		} catch (IOException | InterruptedException e) {
			// Display a popup explaining why the packages could not be installed
			JOptionPane.showMessageDialog(
				null,
				"Could not install depencency packages. Error: " +
				e.getMessage(),
				"Installation Error",
				JOptionPane.ERROR_MESSAGE
			);
			throw new RuntimeException(e);
		}
		return path;
	}

	/**
	 * Get the global configuration.
	 *
	 * @param unpacker The unpacker to use to get the default script and framework paths.
	 * @return The global configuration.
	 */
	private _GlobalData getDefaultGlobalData(ScalpelUnpacker unpacker) {
		final _GlobalData data = new _GlobalData();

		data.defaultScriptPath = unpacker.getDefaultScriptPath();
		data.defaultFrameworkPath = unpacker.getPythonFrameworkPath();
		data.venvPaths = new ArrayList<String>();
		data.venvPaths.add(getOrCreateDefaultVenv());
		data.defaultVenvPath = data.venvPaths.get(0);
		return data;
	}

	/**
	 * Get the project configuration.
	 *
	 * @return The project configuration.
	 */
	private _ProjectData getDefaultProjectData() {
		final _ProjectData data = new _ProjectData();

		data.userScriptPath = globalConfig.defaultScriptPath;
		data.frameworkPath = globalConfig.defaultFrameworkPath;
		data.venvPath = globalConfig.defaultVenvPath;
		return data;
	}

	// Getters

	/*
	 * Get the venv paths list.
	 *
	 * @return The venv paths list.
	 */
	public String[] getVenvPaths() {
		return globalConfig.venvPaths.toArray(new String[0]);
	}

	/*
	 * Get the selected user script path.
	 *
	 * @return The selected user script path.
	 */
	public String getUserScriptPath() {
		return projectConfig.userScriptPath;
	}

	/*
	 * Get the selected framework path.
	 *
	 * @return The selected framework path.
	 */
	public String getFrameworkPath() {
		return projectConfig.frameworkPath;
	}

	/*
	 * Get the selected venv path.
	 *
	 * @return The selected venv path.
	 */
	public String getSelectedVenvPath() {
		return projectConfig.venvPath;
	}

	// Setters

	/*
	 * Set the venv paths list.
	 * Saves the new list to the global configuration file.
	 *
	 * @param venvPaths The new venv paths list.
	 */
	public void setVenvPaths(ArrayList<String> venvPaths) {
		this.globalConfig.venvPaths = venvPaths;
		this.saveGlobalConfig();
	}

	/*
	 * Set the selected user script path.
	 * Saves the new path to the global and project configuration files.
	 *
	 * @param scriptPath The new user script path.
	 */
	public void setUserScriptPath(String scriptPath) {
		this.projectConfig.userScriptPath = scriptPath;
		this.globalConfig.defaultScriptPath = scriptPath;
		this.saveAllConfig();
	}

	/*
	 * Set the selected framework path.
	 * Saves the new path to the global and project configuration files.
	 *
	 * @param frameworkPath The new framework path.
	 */
	public void setFrameworkPath(String frameworkPath) {
		this.projectConfig.frameworkPath = frameworkPath;
		this.globalConfig.defaultFrameworkPath = frameworkPath;
		this.saveAllConfig();
	}

	/*
	 * Set the selected venv path.
	 * Saves the new path to the global and project configuration files.
	 *
	 * @param venvPath The new venv path.
	 */
	public void setSelectedVenvPath(String venvPath) {
		this.projectConfig.venvPath = venvPath;
		this.globalConfig.defaultVenvPath = venvPath;
		this.saveAllConfig();
	}

	// Methods

	/*
	 * Add a venv path to the list.
	 * Saves the new list to the global configuration file.
	 *
	 * @param venvPath The venv path to add.
	 */
	public void addVenvPath(String venvPath) {
		globalConfig.venvPaths.add(venvPath);
		this.saveGlobalConfig();
	}

	/*
	 * Remove a venv path from the list.
	 * Saves the new list to the global configuration file.
	 *
	 * @param venvPath The venv path to remove.
	 */
	public void removeVenvPath(String venvPath) {
		globalConfig.venvPaths.remove(venvPath);
		this.saveGlobalConfig();
	}
}
