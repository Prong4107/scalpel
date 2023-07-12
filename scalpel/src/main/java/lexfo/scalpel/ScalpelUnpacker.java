package lexfo.scalpel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
  Provides methods for unpacking the Scalpel resources.
*/
public class ScalpelUnpacker {

	/**
	    The path to the Scalpel resources directory.
	*/
	private Path ressourcesDirectory;

	/**
	    Returns the path to the Scalpel resources directory.
	    @return The path to the Scalpel resources directory.
	*/
	public String getResourcesPath() {
		return ressourcesDirectory.toString();
	}

	/**
	Returns the path to the Scalpel Python directory.

	<p>Contains the default framework and some samples scripts.</p>

	@return The path to the Scalpel Python directory.
	*/
	public String getPythonPath() {
		return getResourcesPath() + "/python";
	}

	/**
	 * Returns the path to the Scalpel Python framework.
	 *
	 * <p> The framework is a Python script that should wrap the user's script and provide native types
	 *
	 * @return The path to the Scalpel Python framework.
	 */
	public String getPythonFrameworkPath() {
		return getPythonPath() + "/pyscalpel/_framework.py";
	}

	public String getDefaultScriptPath() {
		return getPythonPath() + "/samples/default.py";
	}

	// https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file#:~:text=return%20new%20File(MyClass.class.getProtectionDomain().getCodeSource().getLocation()%0A%20%20%20%20.toURI()).getPath()%3B
	/**
	    Returns the path to the Scalpel JAR file.
	    @return The path to the Scalpel JAR file.
	*/
	private String getRunningJarPath() {
		try {
			return Scalpel.class.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI()
				.getPath();
		} catch (Exception e) {
			return "err";
		}
	}

	// https://stackoverflow.com/questions/9324933/what-is-a-good-java-library-to-zip-unzip-files#:~:text=Extract%20zip%20file%20and%20all%20its%20subfolders%2C%20using%20only%20the%20JDK%3A
	/**
	    Extracts the Scalpel python resources from the Scalpel JAR file.

	    @param zipFile The path to the Scalpel JAR file.
	    @param extractFolder The path to the Scalpel resources directory.
	*/
	private void extractPythonRessources(String zipFile, String extractFolder) {
		ZipFile zip = null;
		try {
			final int BUFFER = 2048;
			final File file = new File(zipFile);
			zip = new ZipFile(file);

			final String newPath = extractFolder;

			new File(newPath).mkdirs();
			final var zipFileEntries = zip.entries();

			// Process each entry
			while (zipFileEntries.hasMoreElements()) {
				// grab a zip file entry
				final ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();

				final String currentEntry = entry.getName();
				final long size = entry.getSize();

				if (!currentEntry.startsWith("python")) {
					continue;
				}

				ScalpelLogger.debug(
					"Extracting " + currentEntry + " (" + size + " bytes)"
				);

				final File destFile = new File(newPath, currentEntry);
				final File destinationParent = destFile.getParentFile();

				// create the parent directory structure if needed
				destinationParent.mkdirs();

				if (!entry.isDirectory()) {
					final var is = zip.getInputStream(entry);

					int currentByte;
					// establish buffer for writing file
					final byte data[] = new byte[BUFFER];

					// write the current file to disk
					final FileOutputStream fos = new FileOutputStream(destFile);
					final var dest = fos;

					// read and write until last byte is encountered
					while ((currentByte = is.read(data)) != -1) {
						dest.write(data, 0, currentByte);
					}
					dest.flush();
					dest.close();
					is.close();
				}
			}
		} catch (Exception e) {
			ScalpelLogger.logStackTrace(e);
		} finally {
			try {
				if (zip != null) zip.close();
			} catch (Exception e) {
				ScalpelLogger.logStackTrace(e);
			}
		}
	}

	/**
	    Initializes the Scalpel resources directory.
	*/
	public void initializeResourcesDirectory() {
		try {
			// Create a $HOME/.scalpel/extracted directory.
			ressourcesDirectory =
				Path.of(
					System.getProperty("user.home"),
					".scalpel",
					"extracted"
				);

			ScalpelLogger.all("Extracting to " + ressourcesDirectory);
			extractPythonRessources(getRunningJarPath(), getResourcesPath());

			ScalpelLogger.all(
				"Successfully extracted running .jar to " + ressourcesDirectory
			);
		} catch (Exception e) {
			ScalpelLogger.error("initializeResourcesDirectory() failed.");
			ScalpelLogger.logStackTrace(e);
		}
	}
}
