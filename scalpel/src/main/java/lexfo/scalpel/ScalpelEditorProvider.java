
package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;

class ScalpelEditorProvider implements HttpRequestEditorProvider
{
    private final MontoyaApi API;

    ScalpelEditorProvider(MontoyaApi API)
    {
        this.API = API;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext)
    {
        return new ScalpelProvidedEditor(API, creationContext);
    }
}
