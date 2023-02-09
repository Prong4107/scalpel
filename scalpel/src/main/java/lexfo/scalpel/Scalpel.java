/*
 * Copyright (c) 2022-2023. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package lexfo.scalpel;

import static burp.api.montoya.ui.editor.EditorOptions.READ_ONLY;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.*;
import jep.Interpreter;
import jep.JepConfig;
import jep.MainInterpreter;
// import jep.PyConfig;
import jep.SharedInterpreter;
import org.apache.commons.io.FileUtils;

//Burp will auto-detect and load any class that extends BurpExtension.
public class Scalpel implements BurpExtension {

  private Logging logging;

  private String tmpJepDirectoryPath;

  private MontoyaApi API;

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
      Enumeration zipFileEntries = zip.entries();

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
      }
    } catch (Exception e) {
      logging.logToError("ERROR: " + e.getMessage());
    }
  }

  private void initializeJepTmpDirectory() {
    try {
      // Generate an unique directory name to avoid "libjep.so already loaded in another classloader"
      tmpJepDirectoryPath = generateTmpDirectoryPath();

      // Init File wrapper for jep directory.
      var dir = new File(tmpJepDirectoryPath);

      // Create dir if not exists
      dir.mkdirs();

      // Clean already existing directory.
      FileUtils.cleanDirectory(dir);

      // Extract running JAR to tmp directory.
      extractFolder(getRunningJarPath(), tmpJepDirectoryPath);

      logging.logToOutput(
        "Successfully extracted running .jar to " + tmpJepDirectoryPath
      );
    } catch (Exception e) {
      logging.logToError("initializeJepTmpDirectory() failed.");
      logging.logToError(e.toString());
    }
  }

  private String[] executeScriptAndCaptureOutput(String scriptContent) {
    try (Interpreter interp = new SharedInterpreter()) {
      // Running Python instructions on the fly.
      // https://github.com/t2y/jep-samples/blob/master/src/HelloWorld.java

      interp.set("montoyaAPI", API);
      interp.exec(
        """
from io import StringIO
import sys
temp_out = StringIO()
temp_err = StringIO()
sys.stdout = temp_out
sys.stderr = temp_err
"""
      );
      interp.exec(scriptContent);
      interp.exec("captured_out = temp_out.getvalue()");
      interp.exec("captured_err = temp_err.getvalue()");

      String capturedOut = (String) interp.getValue("captured_out");
      String capturedErr = (String) interp.getValue("captured_err");
      logging.logToOutput(
        String.format(
          "Executed:\n%s\nOutput:\n%s\nErr:%s\n",
          scriptContent,
          capturedOut,
          capturedErr,
          null
        )
      );
      return new String[] { capturedOut, capturedErr };
    }
  }

  // https://miashs-www.u-ga.fr/prevert/Prog/Java/swing/JTextPane.html
  private Component constructScalpelTab() {
    // Split pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    JSplitPane scriptingPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    JEditorPane editorPane = new JEditorPane();
    JTextArea outputArea = new JTextArea();

    var button = new JButton("Run script.");
    button.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          logging.logToOutput("Clicked button");
          String scriptContent = editorPane.getText();
          try {
            String[] scriptOutput = executeScriptAndCaptureOutput(
              scriptContent
            );
            outputArea.setText(
              String.format(
                "stdout:\n------------------\n%s\n------------------\n\nstderr:\n------------------\n%s",
                scriptOutput[0],
                scriptOutput[1]
              )
            );
          } catch (Exception exception) {
            logging.logToError("Script exec failed:");
            logging.logToError(exception.toString());
          }
          logging.logToOutput("Handled action.");
        }
      }
    );

    editorPane.setText("""
print('This goes in stdout')
print('This goes in stderr', file=sys.stderr)
"""
    );
    outputArea.setEditable(false);

    scriptingPane.setLeftComponent(editorPane);
    scriptingPane.setRightComponent(outputArea);
    scriptingPane.setResizeWeight(0.5);

    splitPane.setResizeWeight(1);
    splitPane.setLeftComponent(scriptingPane);
    splitPane.setRightComponent(button);

    return splitPane;
  }

  @Override
  public void initialize(MontoyaApi api) {
    this.API = api;
    // set extension name
    API.extension().setName("Lexfo Scalpel extension");

    logging = API.logging();

    logging.logToOutput("Start.");

    API.userInterface().registerSuiteTab("Scalpel", constructScalpelTab());

    var initPython = true;

    if (initPython) {
      initializeJepTmpDirectory();

      // PyConfig config = new PyConfig();
      // config.setPythonHome(tmpJepDirectoryPath);
      // MainInterpreter.setInitParams(config);

      MainInterpreter.setJepLibraryPath(
        tmpJepDirectoryPath + "/lib/python3.10/site-packages/jep/libjep.so"
      );

      logging.logToOutput("Initialized successfully.");
    }
  }
}
