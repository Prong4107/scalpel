package lexfo.scalpel;

import java.io.BufferedReader;
import java.io.File;
/**
 * The Venv class is used to manage Python virtual environments.
 */
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manage Python virtual environments.
 */
public class Venv {

	/**
	 * Create a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @return The finished process of the "python3 -m venv" command.
	 */
	public static Process create(String path)
		throws IOException, InterruptedException {
		// Create the directory for the virtual environment
		Path venvPath = Paths.get(path);
		Files.createDirectories(venvPath);

		// Create the virtual environment using the "python3 -m venv" command
		ProcessBuilder processBuilder = new ProcessBuilder(
			Constants.PYTHON_BIN,
			"-m",
			"venv",
			venvPath.toString()
		);
		Process process = processBuilder.start();

		// Wait for the virtual environment creation to complete
		process.waitFor();

		return process;
	}

	public static Process installDefaults(String path)
		throws IOException, InterruptedException {
		// Install the default packages
		return install(path, Constants.PYTHON_DEPENDENCIES);
	}

	/**
	 * Create a virtual environment and install the default packages.
	 *
	 * @param path The path to the virtual environment directory.
	 * @return The exit code of the "pip install ..." command.
	 */
	public static Process createAndInstallDefaults(String path)
		throws IOException, InterruptedException {
		// Create the virtual environment
		final Process proc = create(path);
		if (proc.exitValue() != 0) {
			return proc;
		}
		return installDefaults(path);
	}

	/**
	 * Delete a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 */
	public static void delete(String path) {
		Path venvPath = Paths.get(path);

		try {
			// Delete the virtual environment directory
			Files.delete(venvPath);
			// Return 0 (success)
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Install a package in a virtual environment in a new thread.
	 *
	 * @param path The path to the virtual environment directory.
	 * @param pkgs The name of the package to install.
	 * @return The exit code of the "pip install ..." command.
	 */
	public static Thread install_background(String path, String... pkgs) {
		Thread thread = new Thread(() -> IO.ioWrap(() -> install(path, pkgs)));
		thread.start();
		return thread;
	}

	/**
	 * Install a package in a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @param pkgs The name of the package to install.
	 * @return The exit code of the "pip install ..." command.
	 */
	public static Process install(String path, String... pkgs)
		throws IOException, InterruptedException {
		return install(path, Map.of(), pkgs);
	}

	/**
	 * Install a package in a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @param env The environnement variables to pass
	 * @param pkgs The name of the package to install.
	 * @return The exit code of the "pip install ..." command.
	 */
	public static Process install_background(
		String path,
		Map<String, String> env,
		String... pkgs
	) throws IOException, InterruptedException {
		// Install the package using the "pip install" command

		LinkedList<String> command = new LinkedList<>(
			List.of(getPipPath(path).toString(), "install")
		);
		command.addAll(Arrays.asList(pkgs));
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(Paths.get(path).toFile());
		processBuilder.environment().putAll(env);
		Process process = processBuilder.start();

		System.out.println(
			"Launched " + command.stream().collect(Collectors.joining(" "))
		);

		return process;
	}

	/**
	 * Install a package in a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @param env The environnement variables to pass
	 * @param pkgs The name of the package to install.
	 * @return The exit code of the "pip install ..." command.
	 */
	public static Process install(
		String path,
		Map<String, String> env,
		String... pkgs
	) throws IOException, InterruptedException {
		final var proc = install_background(path, env, pkgs);

		final var stdout = proc.inputReader();
		while (proc.isAlive()) {
			ScalpelLogger.all(stdout.readLine());
		}

		return proc;
	}

	protected static final class PackageInfo {

		public String name;
		public String version;
	}

	public static Path getSitePackagesPath(String venvPath) throws IOException {
		if (Config.isWindows()) {
			// Find the sites-package directory path as in: <path>/Lib/site-packages
			return Files
				.walk(Paths.get(venvPath))
				.filter(Files::isDirectory)
				.filter(p -> p.getFileName().toString().equalsIgnoreCase("lib"))
				.filter(p -> Files.exists(p.resolve("site-packages")))
				.findFirst()
				.orElseThrow(() ->
					new RuntimeException(
						"Failed to find venv site-packages.\n" +
						"Make sure dependencies are correctly installed. (python3,pip,venv,jdk)"
					)
				)
				.resolve("site-packages");
		}
		// Find the sites-package directory path as in: <path>/lib/python*/site-packages
		return Files
			.walk(Paths.get(venvPath, "lib"))
			.filter(Files::isDirectory)
			.filter(p -> p.getFileName().toString().startsWith("python"))
			.filter(p -> Files.exists(p.resolve("site-packages")))
			.findFirst()
			.orElseThrow(() ->
				new RuntimeException(
					"Failed to find venv site-packages.\n" +
					"Make sure dependencies are correctly installed. (python3,pip,venv,jdk)"
				)
			)
			.resolve("site-packages");
	}

	public static Path getExecutablePath(String venvPath, String filename)
		throws IOException {
		final String binDir = Constants.VENV_BIN_DIR;
		final Path validatedVenvPath = Optional
			.ofNullable(new File(venvPath))
			.map(File::toPath)
			.orElseThrow(() ->
				new RuntimeException("Failed to resolve '" + venvPath + "'")
			);

		return Files
			.walk(validatedVenvPath)
			.filter(Files::isDirectory)
			.filter(p -> p.getFileName().toString().equalsIgnoreCase(binDir))
			.map(p -> p.resolve(filename))
			.filter(Files::exists)
			.map(Path::toAbsolutePath)
			.findFirst()
			.orElseThrow(() ->
				new RuntimeException(
					"Failed to find " +
					filename +
					" in " +
					venvPath +
					" .\n" +
					"Make sure dependencies are correctly installed. (python3,pip,venv,jdk)"
				)
			);
	}

	public static Path getPipPath(String venvPath) throws IOException {
		return getExecutablePath(venvPath, Constants.PIP_BIN);
	}

	/**
	 * Get the list of installed packages in a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @return The list of installed packages.
	 */
	public static PackageInfo[] getInstalledPackages(String path)
		throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder(
			getPipPath(path).toString(),
			"list",
			"--format",
			"json",
			"--exclude",
			"pip",
			"--exclude",
			"setuptools"
		);

		// Launch and parse the JSON output using Jackson
		final Process process = processBuilder.start();

		// Read the JSON output
		String jsonData = new String(process.getInputStream().readAllBytes());

		// Parse the JSON output
		return IO.readJSON(jsonData, PackageInfo[].class);
	}
}
