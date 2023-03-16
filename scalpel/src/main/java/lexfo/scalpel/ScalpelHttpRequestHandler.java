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
    // Get a logger object.
    this.logger = API.logging();

    // Keep a reference to the Montoya API.
    this.API = API;

    // Keep a reference to the provider.
    this.editorProvider = editorProvider;

    // Reference the executor to be able to call Python callbacks.
    this.executor = executor;
  }

  @Override
  public RequestToBeSentAction handleHttpRequestToBeSent(
    HttpRequestToBeSent httpRequestToBeSent
  ) {
    // Call the request() Python callback
    var newReq = executor.callIntercepterCallback(httpRequestToBeSent);

    // Return the modified request when requested, else return the original.
    return RequestToBeSentAction.continueWith(
      newReq.orElse(httpRequestToBeSent)
    );
  }

  @Override
  public ResponseReceivedAction handleHttpResponseReceived(
    HttpResponseReceived httpResponseReceived
  ) {
    // Call the request() Python callback
    var newRes = executor.callIntercepterCallback(httpResponseReceived);

    // Return the modified request when requested, else return the original.
    return ResponseReceivedAction.continueWith(
      newRes.orElse(httpResponseReceived)
    );
  }
}
