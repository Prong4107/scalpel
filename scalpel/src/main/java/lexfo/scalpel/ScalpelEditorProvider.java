package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;

class ScalpelEditorProvider
  implements HttpRequestEditorProvider, HttpResponseEditorProvider {

  private final MontoyaApi API;
  private final Logging logger;
  private final ScalpelExecutor executor;

  ScalpelEditorProvider(MontoyaApi API, ScalpelExecutor executor) {
    this.API = API;
    this.logger = API.logging();
    this.executor = executor;
  }

  @Override
  public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(
    EditorCreationContext creationContext
  ) {
    // Instantiate a new request editor.
    ScalpelProvidedEditor editor = new ScalpelProvidedEditor(
      API,
      creationContext,
      EditorType.REQUEST,
      this,
      executor
    );

    // Return the editor.
    return editor;
  }

  @Override
  public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(
    EditorCreationContext creationContext
  ) {
    // Instantiate a new response editor.
    ScalpelProvidedEditor editor = new ScalpelProvidedEditor(
      API,
      creationContext,
      EditorType.RESPONSE,
      this,
      executor
    );
    
    // Return the editor.
    return editor;
  }
}
