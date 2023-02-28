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

    return request;
  }

  @Override
  public HttpResponse getResponse() {
    HttpResponse response = requestResponse != null
      ? requestResponse.response()
      : null;

    return response;
  }

  public HttpRequestResponse getRequestResponse() {
    return requestResponse;
  }

  @Override
  public void setRequestResponse(HttpRequestResponse requestResponse) {
    this.requestResponse = requestResponse;
  }

  private boolean updateContentFromHttpMsg(HttpMessage res) {
    try {
      // Call the Python callback and store the returned value.
      var result = executor.callEditorCallback(res, true, caption());

      // Update the editor's content with the returned bytes.
      result.ifPresent(bytes -> editor.setContents(bytes));

      // Display the tab when bytes are returned.
      return result.isPresent();
    } catch (Exception e) {
      logger.logToError("Error");
      TraceLogger.logExceptionStackTrace(logger, e);
    }
    return false;
  }

  @Override
  public boolean isEnabledFor(HttpRequestResponse requestResponse) {
    // Ensure requestResponse exist.
    if (requestResponse == null) return false;

    // TODO: Directly return false if callback doesn't exist.

    // Call corresponding request editor callback when appropriate.
    if (type == EditorType.REQUEST) return updateContentFromHttpMsg(
      requestResponse.request()
    ); else if (
      requestResponse.response() != null
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
