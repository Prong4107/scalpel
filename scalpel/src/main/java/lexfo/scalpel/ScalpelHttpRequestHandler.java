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
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;

// import java.awt.Component;

public class ScalpelHttpRequestHandler implements HttpHandler {

  private final MontoyaApi API;
  private final Logging logger;
  private final ScalpelEditorProvider editorProvider;

  public ScalpelHttpRequestHandler(
    MontoyaApi API,
    ScalpelEditorProvider editorProvider
  ) {
    this.logger = API.logging();
    this.API = API;
    this.editorProvider = editorProvider;
  }

  @Override
  public RequestToBeSentAction handleHttpRequestToBeSent(
    HttpRequestToBeSent httpRequestToBeSent
  ) {
    var displayedTab = editorProvider.getDisplayedEditor().get();
    Boolean isRequestFromDisplayedTab =
      displayedTab != null &&
      displayedTab.getCtx().toolSource().toolType() == httpRequestToBeSent.toolSource().toolType();
    if (isRequestFromDisplayedTab) {
      // TODO: Call request() callback with mitm req, tab_name and text
      var tab_name = displayedTab.caption();
      var text = displayedTab.getEditor().getContents();

      // request(...)

      // TODO: This should be created from a mitm proxy req modified by Python
      var newReq = HttpRequest.httpRequest(text).withService(httpRequestToBeSent.httpService());

      return RequestToBeSentAction.continueWith(newReq);
    }

    return RequestToBeSentAction.continueWith(httpRequestToBeSent);
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

    return ResponseReceivedAction.continueWith(httpResponseReceived);
  }
}
