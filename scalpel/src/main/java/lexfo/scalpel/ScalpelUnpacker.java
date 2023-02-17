package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;

public class ScalpelUnpacker {

  private String tmpResourcesDirectoryPath;

  private burp.api.montoya.logging.Logging logger;

  public ScalpelUnpacker(Logging logger) {
    this.logger = logger;
  }

  public String getResourcesPath() {
    return tmpResourcesDirectoryPath;
  }

  private static String generateTmpDirectoryPath() {
    return "/tmp/.scalpel_" + UUID.randomUUID().toString();
  }

  // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file#:~:text=return%20new%20File(MyClass.class.getProtectionDomain().getCodeSource().getLocation()%0A%20%20%20%20.toURI()).getPath()%3B
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
  private void extractFolder(String zipFile, String extractFolder) {
    try {
      int BUFFER = 2048;
      File file = new File(zipFile);

      ZipFile zip = new ZipFile(file);
      String newPath = extractFolder;

      new File(newPath).mkdirs();
      var zipFileEntries = zip.entries();

      // Process each entry
      while (zipFileEntries.hasMoreElements()) {
        // grab a zip file entry
        ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
        String currentEntry = entry.getName();

        File destFile = new File(newPath, currentEntry);
        //destFile = new File(newPath, destFile.getName());
        File destinationParent = destFile.getParentFile();

        // create the parent directory structure if needed
        destinationParent.mkdirs();

        if (!entry.isDirectory()) {
          BufferedInputStream is = new BufferedInputStream(
            zip.getInputStream(entry)
          );
          int currentByte;
          // establish buffer for writing file
          byte data[] = new byte[BUFFER];

          // write the current file to disk
          FileOutputStream fos = new FileOutputStream(destFile);
          BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);

          // read and write until last byte is encountered
          while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
            dest.write(data, 0, currentByte);
          }
          dest.flush();
          dest.close();
          is.close();
        }
        zip.close();
      }
    } catch (Exception e) {
      logger.logToError("ERROR: " + e.getMessage());
    }
  }

  public void initializeResourcesDirectory() {
    try {
      // Generate an unique directory name to avoid "libjep.so already loaded in another classloader"
      tmpResourcesDirectoryPath = generateTmpDirectoryPath();

      // Init File wrapper for jep directory.
      var dir = new File(tmpResourcesDirectoryPath);

      // Create dir if not exists
      dir.mkdirs();

      // Clean already existing directory.
      FileUtils.cleanDirectory(dir);

      // Extract running JAR to tmp directory.
      extractFolder(getRunningJarPath(), tmpResourcesDirectoryPath);

      logger.logToOutput(
        "Successfully extracted running .jar to " + tmpResourcesDirectoryPath
      );
    } catch (Exception e) {
      logger.logToError("initializeResourcesDirectory() failed.");
      logger.logToError(e.toString());
    }
  }
}
