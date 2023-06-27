package lexfo.scalpel;

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

/**
 * Manage Python virtual environments.
 */
public class Venv {

	/**
	 * Create a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @return The exit code of the "python3 -m venv" command.
	 */
	public static int create(String path)
		throws IOException, InterruptedException {
		// Create the directory for the virtual environment
		Path venvPath = Paths.get(path);
		Files.createDirectories(venvPath);

		// Create the virtual environment using the "python3 -m venv" command
		ProcessBuilder processBuilder = new ProcessBuilder(
			"python3",
			"-m",
			"venv",
			venvPath.toString()
		);
		Process process = processBuilder.start();

		// Wait for the virtual environment creation to complete and get the exit code
		int exitCode = process.waitFor();

		// Return the exit code (0 indicates success)
		return exitCode;
	}

	/**
	 * Create a virtual environment and install the default packages.
	 *
	 * @param path The path to the virtual environment directory.
	 * @return The exit code of the "pip install ..." command.
	 */
	public static int createAndInstallDefaults(String path)
		throws IOException, InterruptedException {
		// Create the virtual environment
		int exitCode = create(path);
		if (exitCode != 0) {
			return exitCode;
		}

		// Install the default packages
		return install(
			path,
			"mitmproxy",
			"requests",
			"requests-toolbelt",
			"debugpy"
		);
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
	public static int install(String path, String... pkgs)
		throws IOException, InterruptedException {
		// Install the package using the "pip install" command

		LinkedList<String> command = new LinkedList<>(
			List.of("pip", "install")
		);
		command.addAll(Arrays.asList(pkgs));
		command.addAll(List.of("-t", getSitePackagesPath(path).toString()));
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.directory(Paths.get(path).toFile());
		Process process = processBuilder.start();

		// Wait for the package installation to complete and get the exit code
		int exitCode = process.waitFor();

		// Return the exit code (0 indicates success)
		return exitCode;
	}

	protected static final class PackageInfo {

		public String name;
		public String version;
	}

	private static Path getSitePackagesPath(String venvPath)
		throws IOException {
		// Find the sites-package directory path as in: <path>/lib/python*/site-packages
		return Files
			.walk(Paths.get(venvPath, "lib"))
			.filter(Files::isDirectory)
			.filter(p -> p.getFileName().toString().startsWith("python"))
			.filter(p -> Files.exists(p.resolve("site-packages")))
			.findFirst()
			.get()
			.resolve("site-packages");
	}

	/**
	 * Get the list of installed packages in a virtual environment.
	 *
	 * @param path The path to the virtual environment directory.
	 * @return The list of installed packages.
	 */
	public static PackageInfo[] getInstalledPackages(String path)
		throws FileSystemException, IOException {
		Path sitesPackagesPath = getSitePackagesPath(path);

		ProcessBuilder processBuilder = new ProcessBuilder(
			"pip",
			"list",
			"--format",
			"json",
			"--exclude",
			"pip",
			"--exclude",
			"setuptools",
			"--path",
			sitesPackagesPath.toString()
		);

		// Launch and parse the JSON output using Jackson
		Process process = processBuilder.start();

		// Read the JSON output
		String jsonData = new String(process.getInputStream().readAllBytes());

		// Parse the JSON output
		return IO.readJSON(jsonData, PackageInfo[].class);
	}
}
