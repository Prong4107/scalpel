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
    ExtensionProvidedHttpResponseEditor,
    AutoCloseable {

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
    this.API = API;
    this.logger = API.logging();
    this.id = UUID.randomUUID().toString();
    this.provider = provider;
    this.ctx = creationContext;
    this.executor = executor;

    try {
      this.editor = API.userInterface().createRawEditor();
      editor.setEditable(creationContext.editorMode() != EditorMode.READ_ONLY);
      this.type = type;
    } catch (Exception e) {
      logger.logToError("Couldn't instantiate new editor:");
      TraceLogger.logExceptionStackTrace(logger, e);
      throw e;
    }
  }

  public void close() {
    logger.logToOutput("CLOSED " + id);
  }

  public EditorType getEditorType() {
    return type;
  }

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
    return id;
  }

  public EditorCreationContext getCtx() {
    return ctx;
  }

  public RawEditor getEditor() {
    return editor;
  }

  @Override
  public HttpRequest getRequest() {
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
    HttpResponse response = requestResponse != null
      ? requestResponse.response()
      : null;

    if (response == null) return null;

    // TODO: Python-process outbound response.

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
      TraceLogger.logExceptionStackTrace(logger, e);
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
      logger.logToError("buildHttpMsgFromBytes(): Error ");
      TraceLogger.logExceptionStackTrace(logger, e);
    }

    // Nothing was returned and / or an error happened, so we return
    return Optional.empty();
  }

  @Override
  public boolean isEnabledFor(HttpRequestResponse requestResponse) {
    // Ensure requestResponse exist.
    if (requestResponse == null) return false;

    // TODO: Directly return false if callback doesn't exist.

    // Call corresponding request editor callback when appropriate.
    if (
      type == EditorType.REQUEST && requestResponse.request() != null
    ) return updateContentFromHttpMsg(requestResponse.request()); else if (
      type == EditorType.RESPONSE && requestResponse.response() != null
    ) return updateContentFromHttpMsg(requestResponse.response());

    return false;
  }

  @Override
  public String caption() {
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
