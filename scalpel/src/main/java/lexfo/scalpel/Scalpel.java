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
import jep.MainInterpreter;
import lexfo.scalpel.UIBuilder;


//Burp will auto-detect and load any class that extends BurpExtension.
public class Scalpel implements BurpExtension {

  private Logging logger;

  private ScalpelUnpacker unpacker;
  private ScalpelExecutor executor;

  private MontoyaApi API;

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

    // Change this to stop Python from being initialized (for JEP debugging purposes)
    Boolean initPython = true;

    // Init JEP stuff.
    if (initPython) {
      // Instantiate unpacker
      unpacker = new ScalpelUnpacker(logger);

      // Extract the ressources to a new unique temporary directory.
      unpacker.initializeResourcesDirectory();

      // Set Jep path.
      MainInterpreter.setJepLibraryPath(
        unpacker.getResourcesPath() +
        "/lib/python3.10/site-packages/jep/libjep.so"
      );

      // Instantiate the executor (handles Python execution)
      executor =
        new ScalpelExecutor(
          API,
          logger,
          // TODO: CHANGE ME!
          "/home/nol/Desktop/piperpp/scalpel/scripts/editorTest.py"
        );

      // Add the scripting editor tab.
      API.userInterface().registerSuiteTab("ScalpelInterpreter", UIBuilder.constructScalpelInterpreterTab(executor, logger));

      // Add request editor tab
      var provider = new ScalpelEditorProvider(API, executor);

      // Add the request editor to Burp.
      API.userInterface().registerHttpRequestEditorProvider(provider);

      // Add the response editor to Burp.
      API.userInterface().registerHttpResponseEditorProvider(provider);

      // Add an HTTP intercepter to Burp.
      API
        .http()
        .registerHttpHandler(
          new ScalpelHttpRequestHandler(API, provider, executor)
        );
    }

    // Log that the extension has finished loading.
    logger.logToOutput("Initialized successfully.");
  }
}
