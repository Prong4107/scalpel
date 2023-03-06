package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
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
class ScalpelProvidedEditor
  implements
    ExtensionProvidedHttpRequestEditor,
    ExtensionProvidedHttpResponseEditor{

  private final RawEditor editor;
  private HttpRequestResponse requestResponse;
  private final MontoyaApi API;
  private final Logging logger;
  private final EditorCreationContext ctx;
  private final EditorType type;
  private final String id;
  private final ScalpelEditorProvider provider;
  private final ScalpelExecutor executor;

  ScalpelProvidedEditor(
    MontoyaApi API,
    EditorCreationContext creationContext,
    EditorType type,
    ScalpelEditorProvider provider,
    ScalpelExecutor executor
  ) {
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
      editor.setEditable(creationContext.editorMode() != EditorMode.READ_ONLY);

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

  public EditorType getEditorType() {
    // Get the editor type (REQUEST or RESPONSE)
    return type;
  }

  // Print the UI component hierarchy tree.
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

  public String getId() {
    // Get the editor's unique ID.
    return id;
  }

  public EditorCreationContext getCtx() {
    // Get the editor's creation context.
    return ctx;
  }

  public RawEditor getEditor() {
    // Get the editor component.
    return editor;
  }

  @Override
  public HttpRequest getRequest() {
    // Safely extract the request from the requestResponse.
    HttpRequest request = requestResponse != null
      ? requestResponse.request()
      : null;

    // Ensure request exists and has to be processed again before calling Python
    if (editor.isModified() == false || request == null) return request;

    // Call Python "inbound" request editor callback with editor's contents.
    Optional<HttpRequest> msg = pythonBuildHttpMsgFromBytes(
      request,
      editor.getContents()
    );

    // Nothing was returned, return the original request untouched.
    if (msg.isEmpty()) return request;

    // A new request was returned, add the original request
    //  httpService (network stuff) to it and return it.
    return msg.get().withService(request.httpService());
  }

  @Override
  public HttpResponse getResponse() {
    // Safely extract the response from the requestResponse.
    HttpResponse response = requestResponse != null
      ? requestResponse.response()
      : null;

    // Ensure response exists before sending it to Python (TODO).
    if (response == null) return null;

    // TODO: Python-process outbound response.

    // Return the Python-processed response (TODO).
    return response;
  }

  public HttpRequestResponse getRequestResponse() {
    return requestResponse;
  }

  @Override
  public void setRequestResponse(HttpRequestResponse requestResponse) {
    this.requestResponse = requestResponse;
  }

  private boolean updateContentFromHttpMsg(HttpMessage msg) {
    try {
      // Call the Python callback and store the returned value.
      var result = executor.callEditorCallback(msg, true, caption());

      // Update the editor's content with the returned bytes.
      result.ifPresent(bytes -> editor.setContents(bytes));

      // Display the tab when bytes are returned.
      return result.isPresent();
    } catch (Exception e) {
      // Log the error and stack trace.
      TraceLogger.logStackTrace(logger, e);
    }
    // Disable the tab.
    return false;
  }

  @SuppressWarnings("unchecked")
  private static final <
    T extends HttpMessage
  > T cloneHttpMessageAndReplaceBytes(T msg, ByteArray bytes) {
    // Return a HttpRequest or HttpResponse depending on msg real type (HttpRequest or HttpResponse).
    return (T) (
      msg instanceof HttpRequest
        ? HttpRequest.httpRequest(((HttpRequest) msg).httpService(), bytes)
        : HttpResponse.httpResponse(bytes)
    );
  }

  private <T extends HttpMessage> Optional<T> pythonBuildHttpMsgFromBytes(
    T msg,
    ByteArray bytes
  ) {
    try {
      // Call the Python callback and return the result.
      var result = executor.callEditorCallback(msg, bytes, false, caption());

      // When new bytes are returned, use them to create a new http message from the original one.
      if (result.isPresent()) return Optional.of(
        cloneHttpMessageAndReplaceBytes(msg, result.get())
      );
    } catch (Exception e) {
      // Log the function name.
      logger.logToError("buildHttpMsgFromBytes(): Error");

      // Log the error and stack trace.
      TraceLogger.logStackTrace(logger, e);
    }

    // Nothing was returned and / or an error happened, so we return
    return Optional.empty();
  }

  @Override
  public boolean isEnabledFor(HttpRequestResponse requestResponse) {
    // Ensure requestResponse exist.
    if (requestResponse == null) return false;

    // TODO: Directly return false if callback doesn't exist.

    // Get corresponding message.
    HttpMessage msg = (type == EditorType.REQUEST
      ? requestResponse.request()
      : requestResponse.response());

    // Ensure message exists.
    if (msg == null || msg.toByteArray().length() == 0) return false;

    // Call corresponding request editor callback when appropriate.
    return updateContentFromHttpMsg(msg);
  }

  @Override
  public String caption() {
    // Return the tab name.
    return "Scalpel";
  }

  @Override
  public Component uiComponent() {
    return editor.uiComponent();
  }

  @Override
  public Selection selectedData() {
    return editor.selection().isPresent() ? editor.selection().get() : null;
  }

  @Override
  public boolean isModified() {
    return editor.isModified();
  }
}
