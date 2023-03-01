/*
 * Copyright (c) 2023. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.logging.Logging;

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
    // Call the request() Python callback
    var newReq = executor.callRequestToBeSentCallback(httpRequestToBeSent);

    // Return the modified request when requested, else return the original.
    return RequestToBeSentAction.continueWith(
      newReq.orElse(httpRequestToBeSent)
    );
  }

  private static Boolean isAnnotableFromScalpel(Object obj) {
    try {
      Annotations annotations = (Annotations) obj
        .getClass()
        .getMethod("annotations")
        .invoke(obj);

      return (
        annotations != null && annotations.notes().startsWith("scalpel:")
      );
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public ResponseReceivedAction handleHttpResponseReceived(
    HttpResponseReceived httpResponseReceived
  ) {
    var action = ResponseReceivedAction.continueWith(httpResponseReceived);

    ResponseReceivedAction.continueWith(
      executor.callResponseReceivedCallback(httpResponseReceived)
    );

    return action;
  }
}
