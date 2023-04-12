package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

//TODO: File saving/loading

/*
	By default, the project configuration file is located in the $HOME/.scalpel directory.
	(TODO: make this configurable)

	The file name is the project id with the .json extension.
	The project ID is an UUID stored in the extension data:
	https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/persistence/Persistence.html#extensionData()

	Here's a sample configuration file:
	{
		"venvPaths": [
			"/path/to/venv1",
			"/path/to/venv2"
		],
		"scriptPath": "/path/to/script.py",
		"frameworkPath": "/path/to/framework"
	}

	The file is not really designed to be directly edited by the user, but rather by the extension itself.

	A configuration file is needed because we need to store global persistent data arrays. (e.g. venvPaths)
	Which can't be done with the Java Preferences API.
	Furthermore, it's simply more convenient to store as JSON and we already have a scalpel directory to store 'ad-hoc' python venvs.
*/
public class Config {

	// Data class
	private static class _GlobalData {

		public ArrayList<String> venvPaths = new ArrayList<String>();
		public String defaultScriptPath = "";
		public String defaultFrameworkPath = "";
	}

	private static class _ProjectData {

		public String userScriptPath = "";
		public String frameworkPath = "";
	}

	private static final String CONFIG_DIR = ".scalpel";
	private static final String CONFIG_EXT = ".json";
	private final String projectID;
	private final File projectScalpelConfig;

	public static File getScalpelDir() {
		Path home = new File(System.getProperty("user.home")).toPath();

		// Create dir if not exists
		File dir = new File(home.toFile(), CONFIG_DIR);
		if (!dir.exists()) {
			dir.mkdir();
		}

		return dir;
	}

	public static File getGlobalConfigFile() {
		return new File(getScalpelDir(), "global" + CONFIG_EXT);
	}

	private static final String DATA_PREFIX = "scalpel.";
	private static final String DATA_PROJECT_ID_KEY = DATA_PREFIX + "projectID";

	private final _GlobalData globalConfig;
	private final _ProjectData projectConfig;

	private static final ObjectWriter writer = new ObjectMapper()
		.writerWithDefaultPrettyPrinter();

	private static final ObjectMapper mapper = new ObjectMapper();

	private static <T> T readJSON(File file, Class<T> clazz) {
		try {
			return mapper.readValue(file, clazz);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void writeJSON(File file, Object obj) {
		try {
			writer.writeValue(file, obj);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private _GlobalData getDefaultGlobalData(ScalpelUnpacker unpacker) {
		_GlobalData data = new _GlobalData();

		data.defaultScriptPath = unpacker.getDefaultScriptPath();
		data.defaultFrameworkPath = unpacker.getPythonFrameworkPath();
		data.venvPaths = new ArrayList<String>();
		return data;
	}

	private _ProjectData getDefaultProjectData() {
		_ProjectData data = new _ProjectData();

		data.userScriptPath = globalConfig.defaultScriptPath;
		data.frameworkPath = globalConfig.defaultFrameworkPath;
		return data;
	}

	public Config(MontoyaApi API, ScalpelUnpacker unpacker) {
		PersistedObject extensionData = API.persistence().extensionData();
		this.projectID =
			Optional
				.ofNullable(extensionData.getString(DATA_PROJECT_ID_KEY))
				.orElseGet(() -> {
					String id = java.util.UUID.randomUUID().toString();
					extensionData.setString(DATA_PROJECT_ID_KEY, id);
					return id;
				});

		this.projectScalpelConfig =
			new File(getScalpelDir(), projectID + CONFIG_EXT);

		// Load global config
		File globalConfigFile = getGlobalConfigFile();

		globalConfig =
			Optional
				.of(globalConfigFile)
				.filter(File::exists)
				.map(file -> readJSON(file, _GlobalData.class))
				.orElseGet(() -> getDefaultGlobalData(unpacker));

		// Load project config
		projectConfig =
			Optional
				.of(projectScalpelConfig)
				.filter(File::exists)
				.map(file -> readJSON(file, _ProjectData.class))
				.orElseGet(this::getDefaultProjectData);
	}

	private synchronized void saveGlobalConfig() {
		writeJSON(getGlobalConfigFile(), globalConfig);
	}

	private synchronized void saveProjectConfig() {
		writeJSON(projectScalpelConfig, projectConfig);
	}

	public ArrayList<String> getVenvPaths() {
		return globalConfig.venvPaths;
	}

	public String getUserScriptPath() {
		return projectConfig.userScriptPath;
	}

	public String getFrameworkPath() {
		return projectConfig.frameworkPath;
	}

	// Setters
	public void setVenvPaths(ArrayList<String> venvPaths) {
		this.globalConfig.venvPaths = venvPaths;
		this.saveGlobalConfig();
	}

	public void setUserScriptPath(String scriptPath) {
		this.projectConfig.userScriptPath = scriptPath;
		this.saveProjectConfig();
	}

	public void setFrameworkPath(String frameworkPath) {
		this.projectConfig.frameworkPath = frameworkPath;
		this.saveProjectConfig();
	}

	// Methods
	public void addVenvPath(String venvPath) {
		globalConfig.venvPaths.add(venvPath);
		this.saveGlobalConfig();
	}

	public void removeVenvPath(String venvPath) {
		globalConfig.venvPaths.remove(venvPath);
		this.saveGlobalConfig();
	}
}
