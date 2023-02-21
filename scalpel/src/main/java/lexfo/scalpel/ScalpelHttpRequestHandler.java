/*
 * Copyright (c) 2023. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.logging.Logging;

// import java.awt.Component;

public class ScalpelHttpRequestHandler implements HttpHandler {

  private final MontoyaApi API;
  private final Logging logger;
  private final ScalpelEditorProvider editorProvider;
  private final ScalpelExecutor executor;

  public ScalpelHttpRequestHandler(
    MontoyaApi API,
    ScalpelEditorProvider editorProvider,
    ScalpelExecutor executor
  ) {
    this.logger = API.logging();
    this.API = API;
    this.editorProvider = editorProvider;
    this.executor = executor;
  }

  @Override
  public RequestToBeSentAction handleHttpRequestToBeSent(
    HttpRequestToBeSent httpRequestToBeSent
  ) {
    // Keep default behaviour when the request does come from Scalpel editors.
    var defaultAction = RequestToBeSentAction.continueWith(httpRequestToBeSent);

    // Find a displayed editor.
    var foundEditor = editorProvider.getDisplayedRequestEditor();

    // Ensure an editor is displayed.
    if (!foundEditor.isPresent()) return defaultAction;

    // Get the displayed editor.
    var displayedEditor = foundEditor.get();

    // Ensure the requestToBeSent originates from the displayed editor.
    if (
      displayedEditor == null ||
      displayedEditor.getCtx().toolSource().toolType() !=
      httpRequestToBeSent.toolSource().toolType()
    ) return defaultAction;

    // Extract the editor's content.
    var text = displayedEditor.getEditor().getContents();

    // Get the editor tab name.
    var tab_name = displayedEditor.caption();

    // Call the request() Python callback
    var newReq = executor.callRequestToBeSentCallback(
      httpRequestToBeSent,
      text,
      tab_name
    );

    // Return the modified request.
    return RequestToBeSentAction.continueWith(newReq);
  }

  @Override
  public ResponseReceivedAction handleHttpResponseReceived(
    HttpResponseReceived httpResponseReceived
  ) {
    logger.logToOutput(
      "HTTP response from " +
      httpResponseReceived.initiatingRequest().httpService() +
      " [" +
      httpResponseReceived.toolSource().toolType().toolName() +
      "]"
    );

    var newResponse = executor.callResponseReceivedCallback(httpResponseReceived);

    return ResponseReceivedAction.continueWith(newResponse);
  }
}
