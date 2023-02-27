package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
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
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.RuntimeErrorException;

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

  ScalpelProvidedEditor(
    MontoyaApi API,
    EditorCreationContext creationContext,
    EditorType type,
    ScalpelEditorProvider provider
  ) {
    this.API = API;
    logger = API.logging();
    id = UUID.randomUUID().toString();

    try {
      this.provider = provider;

      ctx = creationContext;
      editor = API.userInterface().createRawEditor();
      editor.setEditable(creationContext.editorMode() != EditorMode.READ_ONLY);
      this.type = type;
    } catch (Exception e) {
      logger.logToError("Couldn't instantiate new editor:");
      ExceptionLogger.logStackTrace(logger, e);
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
    if (requestResponse == null) this.editor.setContents(
        ByteArray.byteArray("")
      ); else if (this.type == EditorType.REQUEST) this.editor.setContents(
        requestResponse.request().toByteArray()
      ); else if (requestResponse.response() != null) this.editor.setContents(
        requestResponse.response().toByteArray()
      );

    this.requestResponse = requestResponse;
  }

  @Override
  public boolean isEnabledFor(HttpRequestResponse requestResponse) {
    return true;
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
