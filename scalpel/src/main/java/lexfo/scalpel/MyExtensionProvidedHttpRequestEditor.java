package lexfo.scalpel;

import static burp.api.montoya.core.ByteArray.byteArray;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
// import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
// import burp.api.montoya.utilities.Base64EncodingOptions;
// import burp.api.montoya.utilities.Base64Utils;
import burp.api.montoya.utilities.URLUtils;
import java.awt.*;
import java.util.stream.Collectors;

// import java.util.Optional;

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
class MyExtensionProvidedHttpRequestEditor
  implements ExtensionProvidedHttpRequestEditor {

  private final RawEditor requestEditor;
  private final URLUtils urlUtils;
  private HttpRequestResponse requestResponse;
  private final MontoyaApi api;
  private final Logging logger;

  private ParsedHttpParameter parsedHttpParameter;

  MyExtensionProvidedHttpRequestEditor(
    MontoyaApi api,
    EditorCreationContext creationContext
  ) {
    this.api = api;
    logger = api.logging();
    urlUtils = api.utilities().urlUtils();

    if (creationContext.editorMode() == EditorMode.READ_ONLY) {
      requestEditor =
        api.userInterface().createRawEditor(EditorOptions.READ_ONLY);
    } else {
      requestEditor = api.userInterface().createRawEditor();
    }

    // logger.logToOutput(
    //   Thread.currentThread().getStackTrace()[1].getMethodName()
    // );

    requestEditor.setContents(byteArray("lol"));
  }

  @Override
  public HttpRequest getRequest() {
    // logger.logToOutput(
    //   Thread.currentThread().getStackTrace()[1].getMethodName()
    // );
    return requestResponse.request();
  }

  @Override
  public void setRequestResponse(HttpRequestResponse requestResponse) {
    // logger.logToOutput(
    //   Thread.currentThread().getStackTrace()[1].getMethodName()
    // );
    this.requestResponse = requestResponse;

    String urlDecoded = urlUtils.decode(parsedHttpParameter.value());

    ByteArray output;

    output = byteArray(urlDecoded);

    this.requestEditor.setContents(
        ByteArray.byteArray("ptdr " + output.toString())
      );
  }

  /* 
  // Wrote this because i didnt read the f docs
  // https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/http/message/requests/HttpRequest.html
  private String requestToString(HttpRequest req) {
    return (
      req
        .headers()
        .parallelStream()
        .map(header -> header.toString())
        .collect(Collectors.joining("\r\n")) +
      req.bodyToString()
    );
  }
 */

  private ByteArray transformToHTTP1(ByteArray reqBytes) {
    var http2Match = reqBytes.indexOf("HTTP/2");
    var http2StringLength = "HTTP/2".length();

    // Ensure HTTP/2 is present before modifying stuff
    if (http2Match == -1)
      return reqBytes.copy();

    // Get first line end index
    var firstLineEnd = reqBytes.indexOf("\r\n");

    // Ensure HTTP/2 was matched at the end of the first line
    if (http2Match != firstLineEnd - http2StringLength)
      return reqBytes.copy();

    // Return a new ByteArray with the method changed.
    // Reserve + 2 bytes for HTTP/1%.1%
    var newReqBytes = ByteArray.byteArrayOfLength(reqBytes.length() + 2);
    var reqBytesFirstPart = reqBytes.subArray(0, http2Match);

    // 
    var reqBytesLastPart = reqBytes.subArray(firstLineEnd, reqBytes.length());
    var http1Bytes = ByteArray.byteArray("HTTP/1.1");

    newReqBytes.setBytes(0, reqBytesFirstPart);
    newReqBytes.setBytes(reqBytesFirstPart.length(), http1Bytes);
    newReqBytes.setBytes(reqBytesFirstPart.length() + http1Bytes.length(), reqBytesLastPart);

    return newReqBytes;
  }

  @Override
  public boolean isEnabledFor(HttpRequestResponse requestResponse) {
    // logger.logToOutput(
    //   Thread.currentThread().getStackTrace()[1].getMethodName()
    // );

    // requestResponse.request().
    // logger.logToOutput(requestResponse.request().headers().toString());
    // this.requestEditor.setContents(ByteArray.byteArray(requestToString(requestResponse.request())));
    requestResponse.request().body();
    this.requestEditor.setContents(transformToHTTP1(requestResponse.request().toByteArray()));

    return true;
  }

  @Override
  public String caption() {
    // logger.logToOutput(
    //   Thread.currentThread().getStackTrace()[1].getMethodName()
    // );
    return "Scalpel";
  }

  @Override
  public Component uiComponent() {
    // logger.logToOutput(
    //   Thread.currentThread().getStackTrace()[1].getMethodName()
    // );
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
