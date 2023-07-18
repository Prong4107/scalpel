package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
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
		public String jdkPath = null;
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
	public final String projectID;

	// Path to the project configuration file
	private final File projectScalpelConfig;

	// Prefix for the extension data keys
	private static final String DATA_PREFIX = "scalpel.";

	// Key for the project ID
	private static final String DATA_PROJECT_ID_KEY = DATA_PREFIX + "projectID";

	// Venv that will be created and used when none exists
	public static final String DEFAULT_VENV_NAME = "default";
	public static final String VENV_DIR = "venv";
	private String _jdkPath = null;

	public Config(MontoyaApi API, ScalpelUnpacker unpacker) {
		// Get the extension data to store and get the project ID back.
		final PersistedObject extensionData = API.persistence().extensionData();

		// Get the project ID from the extension data or generate a new one when it doesn't exist.
		this.projectID =
			Optional
				.ofNullable(extensionData.getString(DATA_PROJECT_ID_KEY))
				.orElseGet(() -> {
					String id = UUID.randomUUID().toString();
					extensionData.setString(DATA_PROJECT_ID_KEY, id);
					return id;
				});

		// Set the path to the project configuration file
		this.projectScalpelConfig =
			new File(getScalpelDir(), projectID + CONFIG_EXT);

		this.globalConfig = initGlobalConfig(unpacker);

		this._jdkPath = this.globalConfig.jdkPath;

		// Load project config or create a new one on failure. (e.g. file doesn't exist)
		this.projectConfig = this.initProjectConfig();

		// Write the global config to the file if they didn't exist.
		saveAllConfig();
	}

	private _GlobalData initGlobalConfig(ScalpelUnpacker unpacker) {
		// Load global config
		File globalConfigFile = getGlobalConfigFile();

		// Load global config or create a new one on failure. (e.g. file doesn't exist)
		_GlobalData _globalConfig = Optional
			.of(globalConfigFile)
			.filter(File::exists)
			.map(file -> IO.readJSON(file, _GlobalData.class))
			.map(d -> {
				// Remove venvs that were deleted by an external process.
				d.venvPaths.removeIf(path -> !new File(path).exists());
				if (d.jdkPath == null) {
					d.jdkPath = IO.ioWrap(this::findJdkPath);
				}

				// Ensure that there is at least one venv.
				if (d.venvPaths.size() == 0) {
					d.venvPaths.add(getOrCreateDefaultVenv(d.jdkPath));
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

		return _globalConfig;
	}

	private _ProjectData initProjectConfig() {
		return Optional
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
	}

	public static boolean isWindows() {
		String os = System.getProperty("os.name").toLowerCase();
		return (os.contains("win"));
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

	private static boolean hasIncludeDir(Path jdkPath) {
		var inc = jdkPath.resolve("include").toFile();
		return inc.exists() && inc.isDirectory();
	}

	private static Optional<String> guessJdkPath() throws IOException {
		if (isWindows()) {
			// Official JDK usually gets installed in 'C:\\Program Files\\Java\\jdk-<version>'
			final var winJdkPath = Path.of("C:\\Program Files\\Java\\");
			return Files
				.walk(winJdkPath)
				.filter(f -> f.toFile().getName().contains("jdk"))
				.map(jdk -> jdk.toAbsolutePath())
				.filter(Config::hasIncludeDir)
				.map(jdk -> jdk.toString())
				.findFirst();
		}

		// We try to find the JDK from the javac binary path
		final String binaryName = "javac";
		final var matchingBinaries = findBinaryInPath(binaryName);
		final var potentialJdkPaths = matchingBinaries
			.map(binaryPath -> {
				try {
					final Path absolutePath = Paths
						.get(binaryPath)
						.toRealPath();
					return absolutePath.getParent().getParent();
				} catch (IOException e) {
					return null;
				}
			})
			.filter(path -> path != null);

		// Some distributions (e.g. Kali) come with an incomplete JDK and requires installing a package for the complete one.
		// This filter prevents selecting those.
		final var validJavaHomes = potentialJdkPaths.filter(
			Config::hasIncludeDir
		);

		final var javaHome = validJavaHomes
			.map(path -> path.toString())
			.findFirst();

		return javaHome;
	}

	/**
	 * Tries to get the JDK path from PATH, usual install locations, or by prompting the user.
	 * @return The JDK path.
	 * @throws IOException
	 */
	public String findJdkPath() throws IOException {
		if (_jdkPath != null) {
			// Return memoized path
			return _jdkPath;
		}

		final String javaHome = guessJdkPath()
			.orElseGet(() -> {
				// Display popup telling the user that JDK was not found and needs to select it manually
				JOptionPane.showMessageDialog(
					null,
					"JDK not found. Please select JDK path manually.",
					"JDK not found",
					JOptionPane.INFORMATION_MESSAGE
				);

				// Include a filechooser to choose the path
				final JFileChooser fileChooser = new JFileChooser();
				fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

				final int option = fileChooser.showOpenDialog(null);
				if (option == JFileChooser.APPROVE_OPTION) {
					final File file = fileChooser.getSelectedFile();
					return file.getPath();
				} else {
					return null;
				}
			});

		if (javaHome != null) {
			// Memoize path
			_jdkPath = javaHome;
		}

		return javaHome;
	}

	private static Stream<String> findBinaryInPath(String binaryName) {
		final String systemPath = System.getenv("PATH");
		final String[] pathDirs = systemPath.split(
			System.getProperty("path.separator")
		);

		return Arrays
			.stream(pathDirs)
			.map(pathDir -> Paths.get(pathDir, binaryName))
			.filter(Files::exists)
			.map(Path::toString);
	}

	private static RuntimeException createExceptionFromProcess(
		Process proc,
		String msg,
		String defaultCmdLine
	) {
		final Stream<String> outStream = Stream.concat(
			proc.inputReader().lines(),
			proc.errorReader().lines()
		);
		final String out = outStream.collect(Collectors.joining("\n"));
		final String cmd = proc.info().commandLine().orElse(defaultCmdLine);

		return new RuntimeException(cmd + " failed:\n" + out + "\n" + msg);
	}

	/**
	 * Get the default venv path.
	 * This is the venv that will be used when the project is created.
	 * If the default venv does not exist, it will be created.
	 * If the default venv cannot be created, an exception will be thrown.
	 *
	 * @return The default venv path.
	 */
	public String getOrCreateDefaultVenv(String javaHome) {
		final File defaultPath = Path
			.of(getDefaultVenvsDir().getPath(), DEFAULT_VENV_NAME, VENV_DIR)
			.toFile();

		final String path = defaultPath.getAbsolutePath();

		// Return if default venv dir already exists.
		if (!defaultPath.exists()) {
			defaultPath.mkdirs();
		} else if (!defaultPath.isDirectory()) {
			throw new RuntimeException("Default venv path is not a directory");
		} else {
			return path;
		}

		// Run python -m venv <path>
		try {
			final var proc = Venv.create(path);
			if (proc.exitValue() != 0) {
				throw createExceptionFromProcess(
					proc,
					"Ensure that pip3, python3.*-venv, python >= 3.10 and openjdk >= 17 are installed and in PATH.",
					Constants.PYTHON_BIN + " -m venv " + path
				);
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		// Run pip install <dependencies>
		try {
			final Process proc = Venv.install_background(
				path,
				Map.of("JAVA_HOME", javaHome),
				Constants.PYTHON_DEPENDENCIES
			);

			// Log pip output
			final var stdout = proc.inputReader();
			while (proc.isAlive()) {
				ScalpelLogger.all(stdout.readLine());
			}

			if (proc.exitValue() != 0) {
				throw createExceptionFromProcess(
					proc,
					"Could  not install dependencies\n" +
					"Make sure that openjdk 17 is properly installed and in PATH\n\n" +
					"On Debian/Ubuntu systems:\n\t" +
					"apt install openjdk-17-jdk\n\n" +
					"On Windows:\n\t" +
					"Make sure you have installed Microsoft Visual C++ >=14.0 :\n\t" +
					"https://visualstudio.microsoft.com/visual-cpp-build-tools/",
					"pip install jep ..."
				);
			}
		} catch (Exception e) {
			// Display a popup explaining why the packages could not be installed
			JOptionPane.showMessageDialog(
				null,
				"Could not install depencency packages.\n" +
				"Error: " +
				e.getMessage(),
				"Installation Error",
				JOptionPane.ERROR_MESSAGE
			);
			throw new RuntimeException(e);
		}
		return Path.of(path).getParent().toString();
	}

	/**
	 * Get the global configuration.
	 *
	 * @param unpacker The unpacker to use to get the default script and framework paths.
	 * @return The global configuration.
	 */
	private _GlobalData getDefaultGlobalData(ScalpelUnpacker unpacker) {
		final _GlobalData data = new _GlobalData();

		data.jdkPath = IO.ioWrap(this::findJdkPath, () -> null);
		data.defaultScriptPath = unpacker.getDefaultScriptPath();
		data.defaultFrameworkPath = unpacker.getPythonFrameworkPath();
		data.venvPaths = new ArrayList<String>();
		data.venvPaths.add(getOrCreateDefaultVenv(data.jdkPath));
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

	public String getJdkPath() {
		return globalConfig.jdkPath;
	}

	/*
	 * Get the selected venv path.
	 *
	 * @return The selected venv path.
	 */
	public String getSelectedVenv() {
		return projectConfig.venvPath;
	}

	// Setters

	public void setJdkPath(String path) {
		this.globalConfig.jdkPath = path;
		this.saveGlobalConfig();
	}

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
