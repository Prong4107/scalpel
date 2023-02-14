package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import java.awt.*;


// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
class ScalpelProvidedEditor
  implements ExtensionProvidedHttpRequestEditor {

  private final RawEditor requestEditor;
  private HttpRequestResponse requestResponse;
  private final MontoyaApi API;
  private final Logging logger;

  // private ParsedHttpParameter parsedHttpParameter;

  ScalpelProvidedEditor(
    MontoyaApi API,
    EditorCreationContext creationContext
  ) {
    this.API = API;
    logger = API.logging();

    requestEditor = API.userInterface().createRawEditor();

    requestEditor.setEditable(
      creationContext.editorMode() != EditorMode.READ_ONLY
    );

  }

  @Override
  public HttpRequest getRequest() {

    HttpRequest request;

    if (requestEditor.isModified()) {
      request = HttpRequest.httpRequest(requestEditor.getContents());
    } else {
      request = requestResponse.request();
    }

    return request;
  }

  @Override
  public void setRequestResponse(HttpRequestResponse requestResponse) {

    if (requestResponse.response() == null) this.requestEditor.setContents(
        transformToHTTP1(requestResponse.request().toByteArray())
      );

    this.requestResponse = requestResponse;
  }

  private ByteArray transformToHTTP1(ByteArray reqBytes) {
    var http2Match = reqBytes.indexOf("HTTP/2");
    var http2StringLength = "HTTP/2".length();

    // Ensure HTTP/2 is present before modifying stuff
    if (http2Match == -1) return reqBytes.copy();

    // Get first line end index
    var firstLineEnd = reqBytes.indexOf("\r\n");

    // Ensure HTTP/2 was matched at the end of the first line
    if (http2Match != firstLineEnd - http2StringLength) return reqBytes.copy();

    // Return a new ByteArray with the method changed.
    // Reserve + 2 bytes for HTTP/1%.1%
    var newReqBytes = ByteArray.byteArrayOfLength(reqBytes.length() + 2);
    var reqBytesFirstPart = reqBytes.subArray(0, http2Match);
    var reqBytesLastPart = reqBytes.subArray(firstLineEnd, reqBytes.length());
    var http1Bytes = ByteArray.byteArray("HTTP/1.1");

    newReqBytes.setBytes(0, reqBytesFirstPart);
    newReqBytes.setBytes(reqBytesFirstPart.length(), http1Bytes);
    newReqBytes.setBytes(
      reqBytesFirstPart.length() + http1Bytes.length(),
      reqBytesLastPart
    );

    return newReqBytes;
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
    return requestEditor.uiComponent();
  }

  @Override
  public Selection selectedData() {
    return requestEditor.selection().isPresent()
      ? requestEditor.selection().get()
      : null;
  }

  @Override
  public boolean isModified() {
    return requestEditor.isModified();
  }
}
