package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;

class MyHttpRequestEditorProvider implements HttpRequestEditorProvider
{
    private final MontoyaApi api;

    MyHttpRequestEditorProvider(MontoyaApi api)
    {
        this.api = api;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext)
    {
        return new MyExtensionProvidedHttpRequestEditor(api, creationContext);
    }
}
