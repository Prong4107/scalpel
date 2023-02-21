package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
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

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
class ScalpelProvidedEditor
  implements
    ExtensionProvidedHttpRequestEditor, ExtensionProvidedHttpResponseEditor {

  private final RawEditor editor;
  private HttpRequestResponse requestResponse;
  private final MontoyaApi API;
  private final Logging logger;
  private final EditorCreationContext ctx;
  private final EditorType type;

  ScalpelProvidedEditor(
    MontoyaApi API,
    EditorCreationContext creationContext,
    EditorType type
  ) {
    this.API = API;
    logger = API.logging();
    ctx = creationContext;
    editor = API.userInterface().createRawEditor();
    editor.setEditable(
      creationContext.editorMode() != EditorMode.READ_ONLY
    );
    this.type = type;
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

  @Override
  public void setRequestResponse(HttpRequestResponse requestResponse) {
    if (this.type == EditorType.REQUEST)
      this.editor.setContents(requestResponse.request().toByteArray());
    else if (requestResponse.response() != null)
      this.editor.setContents(requestResponse.response().toByteArray());

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
    return editor.selection().isPresent()
      ? editor.selection().get()
      : null;
  }

  @Override
  public boolean isModified() {
    return editor.isModified();
  }
}
