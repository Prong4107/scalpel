package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import com.google.common.base.Function;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JLayer;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import lexfo.scalpel.ScalpelLogger.Level;

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpResponseEditor.html
/**
  Provides an UI text editor component for editing HTTP requests or responses.
  Calls Python scripts to initialize the editor and update the requests or responses.
*/
public class ScalpelEditorTabbedPane
	implements
		ExtensionProvidedHttpRequestEditor,
		ExtensionProvidedHttpResponseEditor {

	/**
		The editor swing UI component.
	*/
	private final JTabbedPane pane = new JTabbedPane();

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
	private final ScalpelEditorProvider provider;

	/**
		The executor responsible for interacting with Python.
	*/
	private final ScalpelExecutor executor;

	private final ArrayList<ScalpelRawEditor> editors = new ArrayList<>();

	/**
		Constructs a new Scalpel editor.
		
		@param API The Montoya API object.
		@param creationContext The EditorCreationContext object containing information about the editor.
		@param type The editor type (REQUEST or RESPONSE).
		@param provider The ScalpelEditorProvider object that instantiated this editor.
		@param executor The executor to use.
	*/
	ScalpelEditorTabbedPane(
		MontoyaApi API,
		EditorCreationContext creationContext,
		EditorType type,
		ScalpelEditorProvider provider,
		ScalpelExecutor executor
	) {
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

		// Set the editor type (REQUEST or RESPONSE).
		this.type = type;

		try {
			this.recreateEditors();
			ScalpelLogger.log(
				"Successfully initialized ScalpelProvidedEditor for " +
				type.name()
			);
		} catch (Exception e) {
			// Log the stack trace.
			ScalpelLogger.error("Couldn't instantiate new editor:");
			ScalpelLogger.logStackTrace(e);

			// Throw the error again.
			throw e;
		}
	}

	/**
		Recreates the editors tabs.
		
		Calls Python to get the tabs name.
	*/
	public void recreateEditors() {
		// Destroy existing editors
		this.pane.removeAll();
		this.editors.clear();

		// List Python callbacks.
		final List<String> callables;
		try {
			callables = executor.getCallables();
		} catch (RuntimeException e) {
			ScalpelLogger.log(
				Level.TRACE,
				"recreateEditors(): Could not call get_callables"
			);
			return;
		}

		final String prefix =
			(
				type == EditorType.REQUEST
					? Constants.REQ_EDIT_PREFIX
					: Constants.RES_EDIT_PREFIX
			);

		// req_edit_in / res_edit_in
		final String inPrefix = prefix + Constants.IN_SUFFIX;
		// req_edit_out / res_edit_out
		final String outPrefix = prefix + Constants.OUT_SUFFIX;

		// Retain only correct prefixes
		var callbacks = callables
			.stream()
			.filter(c -> c.startsWith(inPrefix) || c.startsWith(outPrefix));

		// Helpers for groupBy
		Function<String, Integer> getOffset =
			(
				name ->
					name.startsWith(inPrefix)
						? inPrefix.length()
						: outPrefix.length()
			);

		// Allow missing suffix (req_edit_in(...) vs req_edit_in_<tabName>(...))
		Function<String, String> getSuffix =
			(n -> n.substring(getOffset.apply(n)).replaceFirst("^_", ""));

		Function<String, String> getPrefix =
			(name -> name.substring(0, getOffset.apply(name)));

		// Group "in" and "out" callbacks by their tab name (suffix)
		Map<String, Set<String>> grouped = callbacks.collect(
			Collectors.groupingBy(
				getSuffix,
				Collectors.mapping(getPrefix, Collectors.toSet())
			)
		);

		grouped.forEach((tabName, cbDirections) -> {
			final ScalpelRawEditor editor = new ScalpelRawEditor(
				tabName,
				cbDirections.contains(outPrefix), // Read-only tab if no out method.
				API,
				ctx,
				type,
				this,
				executor
			);

			this.editors.add(editor);

			if (
				this._requestResponse != null &&
				editor.setRequestResponseInternal(_requestResponse)
			) {
				final var displayedName = tabName.trim().isEmpty()
					? Integer.toString(this.pane.getTabCount())
					: tabName;

				this.pane.addTab(
						displayedName,
						new JLayer<>(editor.uiComponent())
					);
			}
		});
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
			ScalpelLogger.trace(pad.get() + c.hashCode() + ":" + c);
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
	public JTabbedPane getPane() {
		return pane;
	}

	/**
	 * Select the most suited editor for updating Burp message data.
	 *
	 * @return
	 */
	public ScalpelRawEditor selectEditor() {
		final var selectedEditor = editors.get(pane.getSelectedIndex());
		if (selectedEditor.isModified()) {
			return selectedEditor;
		}

		// TODO: Mimic burp update behaviour.
		final var modifiedEditors = editors
			.stream()
			.filter(e -> e.isModified());

		return modifiedEditors.findFirst().orElse(selectedEditor);
	}

	/**
	 * Returns the HTTP message being edited.
	 * @return The HTTP message being edited.
	 */
	public HttpMessage getMessage() {
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
	private HttpMessage processOutboundMessage() {
		return selectEditor().processOutboundMessage();
	}

	/**
	 *  Creates a new HTTP request by passing the editor's contents through a Python callback.
	 * (called by Burp)
	 *
	 * @return The new HTTP request.
	 */
	@Override
	public HttpRequest getRequest() {
		ScalpelLogger.log("getRequest called");
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
		ScalpelLogger.log("getResponse called");
		// Cast the generic HttpMessage interface back to it's concrete type.
		return (HttpResponse) processOutboundMessage();
	}

	/**
		Returns the stored HttpRequestResponse.

		@return The stored HttpRequestResponse.
	*/
	public HttpRequestResponse getRequestResponse() {
		return this._requestResponse;
	}

	/**
		Sets the HttpRequestResponse to be edited.
		(called by Burp)

		@param requestResponse The HttpRequestResponse to be edited.
	*/
	@Override
	public synchronized void setRequestResponse(
		HttpRequestResponse requestResponse
	) {
		this._requestResponse = requestResponse;

		// Hide disabled tabs
		this.pane.removeAll();
		editors
			.parallelStream()
			.filter(e -> e.setRequestResponseInternal(requestResponse))
			.forEach(e ->
				SwingUtilities.invokeLater(() ->
					pane.addTab(e.caption(), e.uiComponent())
				)
			);
	}

	/**
	 * Get the network informations associated with the editor
	 *
	 * Gets the HttpService from requestResponse and falls back to request if it is null
	 *
	 * @return An HttpService if found, else null
	 */
	public HttpService getHttpService() {
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

	/**
		Determines whether the editor should be enabled for the provided HttpRequestResponse.
		Also initializes the editor with Python callbacks output of the inputted HTTP message.
		(called by Burp)

		@param requestResponse The HttpRequestResponse to be edited.
	*/
	@Override
	public boolean isEnabledFor(HttpRequestResponse requestResponse) {
		try {
			return editors
				.parallelStream()
				.anyMatch(e -> e.isEnabledFor(requestResponse));
		} catch (Exception e) {
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
	public String caption() {
		// Return the tab name.
		return "Scalpel";
	}

	/**
		Returns the underlying UI component.
		(called by Burp)

		@return The underlying UI component.
	*/
	@Override
	public Component uiComponent() {
		return this.pane;
	}

	/**
		Returns the selected data.
		(called by Burp)

		@return The selected data.
	*/
	@Override
	public Selection selectedData() {
		return selectEditor().selectedData();
	}

	/**
		Returns whether the editor has been modified.
		(called by Burp)

		@return Whether the editor has been modified.
	*/
	@Override
	public boolean isModified() {
		return editors.stream().anyMatch(e -> e.isModified());
	}
}
