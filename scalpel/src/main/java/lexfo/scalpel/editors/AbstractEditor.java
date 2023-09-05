package lexfo.scalpel.editors;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import java.awt.*;
import java.util.Optional;
import java.util.UUID;
import javax.swing.SwingUtilities;
import lexfo.scalpel.EditorType;
import lexfo.scalpel.ScalpelEditorTabbedPane;
import lexfo.scalpel.ScalpelExecutor;
import lexfo.scalpel.ScalpelLogger;

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpResponseEditor.html
/**
  Base class for implementing Scalpel editors
  It handles all the Python stuff and only leaves the content setter/getter, modification checker and selection parts abstract
  That way, if you wish to implement you own editor, you only have to add logic specific to it (get/set, selected data, has content been modified by user ?)
  */
public abstract class AbstractEditor implements IMessageEditor {

	private final String name;

	/**
		The HTTP request or response being edited.
	*/
	private HttpRequestResponse _requestResponse;

	/**
		The Montoya API object.
	*/
	private final MontoyaApi API;

	/**
		The editor creation context.
	*/
	private final EditorCreationContext ctx;

	/**
		The editor type (REQUEST or RESPONSE).
	*/
	private final EditorType type;

	/**
		The editor ID. (unused)
	*/
	private final String id;

	/**
		The editor provider that instantiated this editor. (unused)
	*/
	private final ScalpelEditorTabbedPane provider;

	/**
		The executor responsible for interacting with Python.
	*/
	private final ScalpelExecutor executor;

	/**
		Constructs a new Scalpel editor.
		
		@param API The Montoya API object.
		@param creationContext The EditorCreationContext object containing information about the editor.
		@param type The editor type (REQUEST or RESPONSE).
		@param provider The ScalpelProvidedEditor object that instantiated this editor.
		@param executor The executor to use.
	*/
	public AbstractEditor(
		String name,
		Boolean editable,
		MontoyaApi API,
		EditorCreationContext creationContext,
		EditorType type,
		ScalpelEditorTabbedPane provider,
		ScalpelExecutor executor
	) {
		this.name = name;

		// Keep a reference to the Montoya API
		this.API = API;

		// Associate the editor with an unique ID (obsolete)
		this.id = UUID.randomUUID().toString();

		// Keep a reference to the provider.
		this.provider = provider;

		// Store the context (e.g.: Tool origin, HTTP message type,...)
		this.ctx = creationContext;

		// Reference the executor to be able to call Python callbacks.
		this.executor = executor;

		this.type = type;
	}

	/**
	 * Set the editor's content
	 *
	 * Note: This should update isModified()
	 * @param bytes The new content
	 */
	protected abstract void setEditorContent(ByteArray bytes);

	/**
	 * Get the editor's content
	 * @return The editor's content
	 */
	protected abstract ByteArray getEditorContent();

	/**
		Returns the underlying UI component.

		@return The underlying UI component.
	*/
	@Override
	public abstract Component uiComponent();

	/**
		Returns the selected data.
		(called by Burp)

		@return The selected data.
	*/
	@Override
	public abstract Selection selectedData();

	/**
		Returns whether the editor has been modified since the last time it was programatically set
		(called by Burp)

		@return Whether the editor has been modified.
	*/
	@Override
	public abstract boolean isModified();

	/**
		Returns the editor type (REQUEST or RESPONSE).

		@return The editor type (REQUEST or RESPONSE).
	*/
	public final EditorType getEditorType() {
		return type;
	}

	/**
		Returns the editor's unique ID. (unused)
		@return The editor's unique ID.
	*/
	public final String getId() {
		return id;
	}

	/**
		Returns the editor's creation context.
		@return The editor's creation context.
	*/
	public final EditorCreationContext getCtx() {
		return ctx;
	}

	/**
	 * Returns the HTTP message being edited.
	 * @return The HTTP message being edited.
	 */
	public final HttpMessage getMessage() {
		// Ensure request response exists.
		if (_requestResponse == null) {
			return null;
		}

		// Safely extract the message from the requestResponse.
		return type == EditorType.REQUEST
			? _requestResponse.request()
			: _requestResponse.response();
	}

	/**
	 *  Creates a new HTTP message by passing the editor's contents through a Python callback.
	 *
	 * @return The new HTTP message.
	 */
	public final HttpMessage processOutboundMessage() {
		try {
			// Safely extract the message from the requestResponse.
			final HttpMessage msg = getMessage();

			// Ensure request exists and has to be processed again before calling Python
			if (msg == null || !isModified()) {
				return null;
			}

			final Optional<HttpMessage> result;

			// Call Python "outbound" message editor callback with editor's contents.
			if (type == EditorType.REQUEST) {
				result =
					executor
						.callEditorCallbackOutRequest(
							_requestResponse.request(),
							getHttpService(),
							getEditorContent(),
							caption()
						)
						.flatMap(Optional::of);
			} else {
				result =
					executor
						.callEditorCallbackOutResponse(
							_requestResponse.response(),
							_requestResponse.request(),
							getHttpService(),
							getEditorContent(),
							caption()
						)
						.flatMap(Optional::of);
			}

			// Nothing was returned, return the original msg untouched.
			if (result.isEmpty()) {
				return msg;
			}

			// Return the Python-processed message.
			return result.get();
		} catch (Exception e) {
			ScalpelLogger.logStackTrace(e);
		}
		return null;
	}

	/**
	 *  Creates a new HTTP request by passing the editor's contents through a Python callback.
	 * (called by Burp)
	 *
	 * @return The new HTTP request.
	 */
	@Override
	public final HttpRequest getRequest() {
		// Cast the generic HttpMessage interface back to it's concrete type.
		return (HttpRequest) processOutboundMessage();
	}

	/**
	 *  Creates a new HTTP response by passing the editor's contents through a Python callback.
	 * (called by Burp)
	 *
	 * @return The new HTTP response.
	 */
	@Override
	public final HttpResponse getResponse() {
		// Cast the generic HttpMessage interface back to it's concrete type.
		return (HttpResponse) processOutboundMessage();
	}

	/**
		Returns the stored HttpRequestResponse.

		@return The stored HttpRequestResponse.
	*/
	public HttpRequestResponse getRequestResponse() {
		return _requestResponse;
	}

	// Returns a bool and avoids making duplicate calls to isEnabledFor to know if callback succeeded
	public final boolean setRequestResponseInternal(
		HttpRequestResponse requestResponse
	) {
		this._requestResponse = requestResponse;
		return updateContent(requestResponse);
	}

	/**
		Sets the HttpRequestResponse to be edited.
		(called by Burp)

		@param requestResponse The HttpRequestResponse to be edited.
	*/
	@Override
	public final void setRequestResponse(HttpRequestResponse requestResponse) {
		setRequestResponseInternal(requestResponse);
	}

	/**
	 * Get the network informations associated with the editor
	 *
	 * Gets the HttpService from requestResponse and falls back to request if it is null
	 *
	 * @return An HttpService if found, else null
	 */
	public final HttpService getHttpService() {
		final HttpRequestResponse reqRes = this._requestResponse;

		// Ensure editor is initialized
		if (reqRes == null) return null;

		// Check if networking infos are available in the requestRespone
		if (reqRes.httpService() != null) {
			return reqRes.httpService();
		}

		// Fall back to the initiating request
		final HttpRequest req = reqRes.request();
		if (req != null) {
			return req.httpService();
		}

		return null;
	}

	public final Optional<ByteArray> executeCallback(
		HttpRequestResponse reqRes
	) throws Exception {
		if (reqRes == null) {
			return Optional.empty();
		}

		if (type == EditorType.REQUEST && reqRes.request() != null) {
			return executor.callEditorCallbackInRequest(
				reqRes.request(),
				getHttpService(),
				caption()
			);
		} else if (type == EditorType.RESPONSE && reqRes.response() != null) {
			return executor.callEditorCallbackInResponse(
				reqRes.response(),
				reqRes.request(),
				getHttpService(),
				caption()
			);
		}
		return Optional.empty();
	}

	/**
		Initializes the editor with Python callbacks output of the inputted HTTP message.
		@param msg The HTTP message to be edited.

		@return True when the Python callback returned bytes, false otherwise.
	*/
	public final boolean updateContent(HttpRequestResponse reqRes) {
		final Optional<ByteArray> result;
		try {
			result = executeCallback(reqRes);
		} catch (Exception e) {
			ScalpelLogger.logStackTrace(e);

			// Disable the tab.
			return false;
		}

		// Update the editor's content with the returned bytes.
		result.ifPresent(bytes ->
			SwingUtilities.invokeLater(() -> setEditorContent(bytes))
		);

		// Display the tab when bytes are returned.
		return result.isPresent();
	}

	/**
		Determines whether the editor should be enabled for the provided HttpRequestResponse.
		Also initializes the editor with Python callbacks output of the inputted HTTP message.
		(called by Burp)

		@param reqRes The HttpRequestResponse to be edited.
	*/
	@Override
	public final boolean isEnabledFor(HttpRequestResponse reqRes) {
		if (reqRes == null) {
			return true;
		}

		// Extract the message from the reqRes.
		final HttpMessage msg =
			(type == EditorType.REQUEST ? reqRes.request() : reqRes.response());

		// Ensure message exists.
		if (msg == null || msg.toByteArray().length() == 0) {
			return true;
		}

		try {
			// Call corresponding request editor callback when appropriate.
			return executeCallback(reqRes).isPresent();
		} catch (Exception e) {
			// Log the error trace.
			ScalpelLogger.logStackTrace(e);
		}
		return false;
	}

	/**
		Returns the name of the tab.
		(called by Burp)

		@return The name of the tab.
	*/
	@Override
	public final String caption() {
		return this.name;
	}
}
