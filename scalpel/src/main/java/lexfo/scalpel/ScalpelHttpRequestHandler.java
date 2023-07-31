/*
 * Copyright (c) 2023. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import java.util.Optional;

/**
  Handles HTTP requests and responses.
*/
public class ScalpelHttpRequestHandler implements HttpHandler {

	/**
    	The MontoyaApi object used to interact with Burp Suite.
  	*/
	private final MontoyaApi API;

	/**
    	The ScalpelEditorProvider object used to provide new 	editors.
	*/
	private final ScalpelEditorProvider editorProvider;

	/**
    	The ScalpelExecutor object used to execute Python scripts.
  	*/
	private final ScalpelExecutor executor;

	/**
		Constructs a new ScalpelHttpRequestHandler object with the specified MontoyaApi object and ScalpelExecutor object.
		@param API The MontoyaApi object to use.
		@param editorProvider The ScalpelEditorProvider object to use.
		@param executor The ScalpelExecutor object to use.
  	*/
	public ScalpelHttpRequestHandler(
		MontoyaApi API,
		ScalpelEditorProvider editorProvider,
		ScalpelExecutor executor
	) {
		// Keep a reference to the Montoya API.
		this.API = API;

		// Keep a reference to the provider.
		this.editorProvider = editorProvider;

		// Reference the executor to be able to call Python callbacks.
		this.executor = executor;
	}

	/**
		Handles HTTP requests.
		@param httpRequestToBeSent The HttpRequestToBeSent object containing information about the HTTP request.
		@return A RequestToBeSentAction object containing information about how to handle the HTTP request.
	*/
	@Override
	public RequestToBeSentAction handleHttpRequestToBeSent(
		HttpRequestToBeSent httpRequestToBeSent
	) {
		// Call the request() Python callback
		final Optional<HttpRequest> newReq = executor.callIntercepterCallback(
			httpRequestToBeSent,
			httpRequestToBeSent.httpService()
		);

		// Return the modified request when requested, else return the original.
		return RequestToBeSentAction.continueWith(
			newReq.orElse(httpRequestToBeSent)
		);
	}

	/**
		Handles HTTP responses.
		@param httpResponseReceived The HttpResponseReceived object containing information about the HTTP response.
		@return A ResponseReceivedAction object containing information about how to handle the HTTP response.
  	*/
	@Override
	public ResponseReceivedAction handleHttpResponseReceived(
		HttpResponseReceived httpResponseReceived
	) {
		// Get the network info form the initiating request.
		final HttpService service = Optional
			.ofNullable(httpResponseReceived.initiatingRequest())
			.map(HttpRequest::httpService)
			.orElse(null);

		// Call the request() Python callback
		final var newRes = executor.callIntercepterCallback(
			httpResponseReceived,
			service
		);

		// Return the modified request when requested, else return the original.
		return ResponseReceivedAction.continueWith(
			newRes.orElse(httpResponseReceived)
		);
	}
}
