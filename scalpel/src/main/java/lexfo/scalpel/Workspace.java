package lexfo.scalpel;

import com.jediterm.terminal.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import org.apache.commons.io.FileUtils;

/* 
 *  Note: The Scalpel data folder follows this architeture:
 * 
    ~
    └── .scalpel
        ├── extracted (ressources)
        ├── global.json
        ├── <project-data>.json
        └── venvs
            └── <workspace-name>
                ├── default.py
                └── .venv
*/

/**
 *  A workspace is a folder containing a venv and the associated scripts.
 * <br />
 *  We may still call that a "venv" in the front-end to avoid confusing the user.
 */
public class Workspace {

	public static final String VENV_DIR = ".venv";
	public static final String DEFAULT_VENV_NAME = "default";

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
	 * Copy the script to the selected workspace
	 * @param scriptPath The script to copy
	 * @return The new file path
	 */
	public static Path copyScriptToWorkspace(
		final Path workspace,
		final Path scriptPath
	) {
		final File original = scriptPath.toFile();
		final String baseErrMsg =
			"Could not copy " + scriptPath + " to " + workspace + "\n";

		final Path destination = Optional
			.ofNullable(original)
			.filter(File::exists)
			.map(File::getName)
			.map(workspace::resolve)
			.orElseThrow(() ->
				new RuntimeException(baseErrMsg + "File not found")
			);

		if (Files.exists(destination)) {
			throw new RuntimeException(baseErrMsg + "File already exists");
		}

		try {
			return Files
				.copy(
					original.toPath(),
					destination,
					StandardCopyOption.REPLACE_EXISTING
				)
				.toAbsolutePath();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void copyWorkspaceFiles(Path workspace) throws IOException {
		final File source = RessourcesUnpacker.WORKSPACE_PATH.toFile();
		final File dest = workspace.toFile();
		FileUtils.copyDirectory(source, dest, true);
	}

	public static void createAndInitWorkspace(
		Path workspace,
		Optional<Path> javaHome
	) {
		// Run python -m venv <path>
		try {
			final var venvDir = getVenvDir(workspace);
			final var proc = Venv.create(venvDir);
			if (proc.exitValue() != 0) {
				throw createExceptionFromProcess(
					proc,
					"Ensure that pip3, python3.*-venv, python >= 3.10 and openjdk >= 17 are installed and in PATH.",
					Constants.PYTHON_BIN + " -m venv " + workspace
				);
			}
			copyWorkspaceFiles(workspace);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}

		try {
			// Add default script.
			copyScriptToWorkspace(
				workspace,
				RessourcesUnpacker.DEFAULT_SCRIPT_PATH
			);
		} catch (RuntimeException e) {
			ScalpelLogger.error(
				"Default script could not be copied to " + workspace
			);
		}

		// Run pip install <dependencies>
		try {
			final Process proc;

			if (javaHome.isPresent()) {
				proc =
					Venv.installDefaults(
						workspace,
						Map.of("JAVA_HOME", javaHome.map(Path::toString).get()),
						true
					);
			} else {
				proc = Venv.installDefaults(workspace);
			}

			// Log pip output
			final var stdout = proc.inputReader();
			while (proc.isAlive()) {
				Optional
					.ofNullable(stdout.readLine())
					.ifPresent(ScalpelLogger::all);
			}

			if (proc.exitValue() != 0) {
				final String linuxMsg =
					"On Debian/Ubuntu systems:\n\t" +
					"apt install build-essential python3-dev openjdk-17-jdk";

				final String winMsg =
					"On Windows:\n\t" +
					"Make sure you have installed Microsoft Visual C++ >=14.0 :\n\t" +
					"https://visualstudio.microsoft.com/visual-cpp-build-tools/";

				final String msg = UIUtil.isWindows ? winMsg : linuxMsg;

				throw createExceptionFromProcess(
					proc,
					"Could  not install dependencies\n" +
					"Make sure that a compiler, python dev libraries and openjdk 17 are properly installed and in PATH\n\n" +
					msg,
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
	}

	/**
	 * Get the default workspace path.
	 * This is the workspace that will be used when the project is created.
	 * If the default workspace does not exist, it will be created.
	 * If the default workspace cannot be created, an exception will be thrown.
	 *
	 * @return The default workspace path.
	 */
	public static Path getOrCreateDefaultWorkspace(Path javaHome) {
		final Path workspace = Path.of(
			Workspace.getWorkspacesDir().getPath(),
			DEFAULT_VENV_NAME
		);

		final File venvDir = workspace.resolve(VENV_DIR).toFile();

		// Return if default workspace dir already exists.
		if (!venvDir.exists()) {
			venvDir.mkdirs();
		} else if (!venvDir.isDirectory()) {
			throw new RuntimeException("Default venv path is not a directory");
		} else {
			return workspace;
		}

		createAndInitWorkspace(
			workspace.toAbsolutePath(),
			Optional.of(javaHome)
		);

		return workspace;
	}

	public static Path getVenvDir(Path workspace) {
		return workspace.resolve(VENV_DIR);
	}

	public static Path getDefaultWorkspace() {
		return Paths
			.get(getWorkspacesDir().getAbsolutePath())
			.resolve(DEFAULT_VENV_NAME);
	}

	/**
	 * Get the scalpel configuration directory.
	 *
	 * @return The scalpel configuration directory. (default: $HOME/.scalpel)
	 */
	public static File getScalpelDir() {
		final File dir = RessourcesUnpacker.DATA_DIR_PATH.toFile();
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
	public static File getWorkspacesDir() {
		final File dir = new File(getScalpelDir(), "venvs");
		if (!dir.exists()) {
			dir.mkdir();
		}

		return dir;
	}
}
