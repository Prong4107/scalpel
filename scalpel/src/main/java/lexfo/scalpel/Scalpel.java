/*
 * Copyright (c) 2022-2023. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import jep.MainInterpreter;

//Burp will auto-detect and load any class that extends BurpExtension.
public class Scalpel implements BurpExtension {

  private Logging logger;

  private ScalpelUnpacker unpacker;
  private ScalpelExecutor executor;

  private MontoyaApi API;

  private JEditorPane editorPane;

  // https://miashs-www.u-ga.fr/prevert/Prog/Java/swing/JTextPane.html
  private Component constructScalpelTab() {
    // Split pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    JSplitPane scriptingPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    JTextArea outputArea = new JTextArea();
    editorPane = new JEditorPane();
    JButton button = new JButton("Run script.");

    button.addActionListener((ActionEvent e) -> {
      logger.logToOutput("Clicked button");
      String scriptContent = editorPane.getText();
      try {
        String[] scriptOutput = executor.evalAndCaptureOutput(scriptContent);

        var txt = String.format(
          "stdout:\n------------------\n%s\n------------------\n\nstderr:\n------------------\n%s",
          scriptOutput[0],
          scriptOutput[1]
        );

        outputArea.setText(txt);
      } catch (Exception exception) {
        outputArea.setText(exception.toString());
      }
      logger.logToOutput("Handled action.");
    });

    editorPane.setText(
      """
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
  public void initialize(MontoyaApi API) {
    // Init API member.
    this.API = API;

    // Set extension name.
    API.extension().setName("Lexfo Scalpel extension");

    // Init logger member.
    logger = API.logging();

    // Show that the extension is loading.
    logger.logToOutput("Initializing...");

    // Add the scripting editor tab.
    API.userInterface().registerSuiteTab("Scalpel", constructScalpelTab());

    // Change this to stop Python from being initialized (for JEP debugging purposes)
    Boolean initPython = true;

    // Init JEP stuff.
    if (initPython) {
      // Instantiate unpacker
      unpacker = new ScalpelUnpacker(logger);

      // TODO: Remove this stuff if not necessary
      unpacker.initializeResourcesDirectory();

      MainInterpreter.setJepLibraryPath(
        unpacker.getResourcesPath() +
        "/lib/python3.10/site-packages/jep/libjep.so"
      );

      executor =
        new ScalpelExecutor(
          API,
          logger,
          // TODO: CHANGE ME!
          "/home/nol/Desktop/piperpp/scalpel/scripts/editorTest.py"
        );

      // Add request editor tab
      // https://github.com/PortSwigger/burp-extensions-montoya-api-examples/blob/main/customrequesteditortab/src/main/java/example/customrequesteditortab/CustomRequestEditorTab.java
      var provider = new ScalpelEditorProvider(API, executor);

      API.userInterface().registerHttpRequestEditorProvider(provider);
      API.userInterface().registerHttpResponseEditorProvider(provider);
      API
        .http()
        .registerHttpHandler(
          new ScalpelHttpRequestHandler(API, provider, executor)
        );
    }
    logger.logToOutput("Initialized successfully.");
  }
}
