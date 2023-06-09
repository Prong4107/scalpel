package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import java.awt.*;
import java.util.LinkedList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpResponseEditor.html
/**
  Provides an UI text editor component for editing HTTP requests or responses.
  Calls Python scripts to initialize the editor and update the requests or responses.
*/
public class ScalpelConcreteEditor
	implements
		ExtensionProvidedHttpRequestEditor,
		ExtensionProvidedHttpResponseEditor {

	private final String name;
	/**
		The editor swing UI component.
	*/
	private final RawEditor editor;

	/**
		The HTTP request or response being edited.
	*/
	private HttpRequestResponse requestResponse;

	/**
		The Montoya API object.
	*/
	private final MontoyaApi API;

	/**
		The logger object.
	*/
	private final Logging logger;

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
	private final ScalpelProvidedEditor provider;

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
	ScalpelConcreteEditor(
		String name,
		MontoyaApi API,
		EditorCreationContext creationContext,
		EditorType type,
		ScalpelProvidedEditor provider,
		ScalpelExecutor executor
	) {
		this.name = name;

		// Keep a reference to the Montoya API
		this.API = API;

		// Get a logger
		this.logger = API.logging();

		// Associate the editor with an unique ID (obsolete)
		this.id = UUID.randomUUID().toString();

		// Keep a reference to the provider.
		this.provider = provider;

		// Store the context (e.g.: Tool origin, HTTP message type,...)
		this.ctx = creationContext;

		// Reference the executor to be able to call Python callbacks.
		this.executor = executor;

		try {
			// Create a new editor UI component.
			this.editor = API.userInterface().createRawEditor();

			// Decide wherever the editor must be editable or read only depending on context.
			editor.setEditable(
				creationContext.editorMode() != EditorMode.READ_ONLY
			);

			// Set the editor type (REQUEST or RESPONSE).
			this.type = type;
		} catch (Exception e) {
			// Log the error.
			logger.logToError("Couldn't instantiate new editor:");

			// Log the stack trace.
			TraceLogger.logStackTrace(logger, e);

			// Throw the error again.
			throw e;
		}
	}

	/**
		Returns the editor type (REQUEST or RESPONSE).

		@return The editor type (REQUEST or RESPONSE).
	*/
	public EditorType getEditorType() {
		return type;
	}

	/**
		Prints the UI component hierarchy tree. (debugging)
	*/
	private void printUiTrace() {
		LinkedList<Container> lst = new LinkedList<>();
		Container current = uiComponent().getParent();

		while (current != null) {
			lst.push(current);
			current = current.getParent();
		}

		// https://stackoverflow.com/questions/38402493/local-variable-log-defined-in-an-enclosing-scope-must-be-final-or-effectively-fi
		final AtomicReference<String> pad = new AtomicReference<>("");
		lst.forEach(c -> {
			logger.logToOutput(pad.get() + c.hashCode() + ":" + c);
			pad.set(pad.get() + "  ");
		});
	}

	/**
		Returns the editor's unique ID. (unused)
		@return The editor's unique ID.
	*/
	public String getId() {
		return id;
	}

	/**
		Returns the editor's creation context.
		@return The editor's creation context.
	*/
	public EditorCreationContext getCtx() {
		return ctx;
	}

	/**
		Returns the Burp editor object.
		@return The Burp editor object.
	*/
	public RawEditor getEditor() {
		return editor;
	}

	/**
	 * Returns the HTTP message being edited.
	 * @return The HTTP message being edited.
	 */
	public HttpMessage getMessage() {
		// Ensure request response exists.
		if (requestResponse == null) return null;

		// Safely extract the message from the requestResponse.
		return type == EditorType.REQUEST
			? requestResponse.request()
			: requestResponse.response();
	}

	/**
	 *  Creates a new HTTP message by passing the editor's contents through a Python callback.
	 *
	 * @return The new HTTP message.
	 */
	public HttpMessage processOutboundMessage() {
		try {
			// Safely extract the message from the requestResponse.
			final HttpMessage msg = getMessage();

			// Ensure request exists and has to be processed again before calling Python
			if (msg == null || !editor.isModified()) return null;

			// Call Python "outbound" message editor callback with editor's contents.
			final Optional<HttpMessage> result = executor.callEditorCallbackOut(
				msg,
				getHttpService(),
				editor.getContents(),
				caption()
			);

			// Nothing was returned, return the original msg untouched.
			if (result.isEmpty()) return msg;

			// Return the Python-processed message.
			return result.get();
		} catch (Exception e) {
			TraceLogger.logStackTrace(logger, e);
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
	public HttpRequest getRequest() {
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
	public HttpResponse getResponse() {
		// Cast the generic HttpMessage interface back to it's concrete type.
		return (HttpResponse) processOutboundMessage();
	}

	/**
		Returns the stored HttpRequestResponse.

		@return The stored HttpRequestResponse.
	*/
	public HttpRequestResponse getRequestResponse() {
		return requestResponse;
	}

	/**
		Sets the HttpRequestResponse to be edited.
		(called by Burp)

		@param requestResponse The HttpRequestResponse to be edited.
	*/
	@Override
	public void setRequestResponse(HttpRequestResponse requestResponse) {
		this.requestResponse = requestResponse;
	}

	/**
	 * Get the network informations associated with the editor
	 *
	 * Gets the HttpService from requestResponse and falls back to request if it is null
	 *
	 * @return An HttpService if found, else null
	 */
	public HttpService getHttpService() {
		final HttpRequestResponse reqRes = this.requestResponse;

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

	/**
		Initializes the editor with Python callbacks output of the inputted HTTP message.
		@param msg The HTTP message to be edited.

		@return True when the Python callback returned bytes, false otherwise.
	*/
	public boolean updateContentFromHttpMsg(HttpMessage msg) {
		try {
			// Call the Python callback and store the returned value.
			final var result = executor.callEditorCallback(
				msg,
				getHttpService(),
				true,
				caption()
			);

			// Update the editor's content with the returned bytes.
			result.ifPresent(bytes -> editor.setContents(bytes));

			// Display the tab when bytes are returned.
			return result.isPresent();
		} catch (Exception e) {
			TraceLogger.logStackTrace(logger, e);
		}
		// Disable the tab.
		return false;
	}

	/**
		Determines whether the editor should be enabled for the provided HttpRequestResponse.
		Also initializes the editor with Python callbacks output of the inputted HTTP message.
		(called by Burp)

		@param requestResponse The HttpRequestResponse to be edited.
	*/
	@Override
	public boolean isEnabledFor(HttpRequestResponse requestResponse) {
		try {
			// Initialize requestResponse member.
			this.requestResponse =
				requestResponse == null
					? this.requestResponse
					: requestResponse;

			// Extract the message from the requestResoponse.
			final HttpMessage msg = getMessage();

			// Ensure message exists.
			if (msg == null || msg.toByteArray().length() == 0) return false;

			// Call corresponding request editor callback when appropriate.
			return updateContentFromHttpMsg(msg);
		} catch (Exception e) {
			// Log the error trace.
			TraceLogger.logStackTrace(logger, e);
		}
		return false;
	}

	/**
		Returns the name of the tab.
		(called by Burp)

		@return The name of the tab.
	*/
	@Override
	public String caption() {
		// Return the tab name.
		return this.name;
	}

	/**
		Returns the underlying UI component.
		(called by Burp)

		@return The underlying UI component.
	*/
	@Override
	public Component uiComponent() {
		// return new JFileChooser();
		return editor.uiComponent();
	}

	/**
		Returns the selected data.
		(called by Burp)

		@return The selected data.
	*/
	@Override
	public Selection selectedData() {
		return editor.selection().isPresent() ? editor.selection().get() : null;
	}

	/**
		Returns whether the editor has been modified.
		(called by Burp)

		@return Whether the editor has been modified.
	*/
	@Override
	public boolean isModified() {
		return editor.isModified();
	}
}
