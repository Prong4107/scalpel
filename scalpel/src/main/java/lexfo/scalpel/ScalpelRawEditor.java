package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import java.awt.*;

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpResponseEditor.html
/**
  Provides an UI text editor component for editing HTTP requests or responses.
  Calls Python scripts to initialize the editor and update the requests or responses.
*/
public class ScalpelRawEditor extends AbstractEditor {

	private final RawEditor editor;

	/**
		Constructs a new Scalpel editor.
		
		@param API The Montoya API object.
		@param creationContext The EditorCreationContext object containing information about the editor.
		@param type The editor type (REQUEST or RESPONSE).
		@param provider The ScalpelProvidedEditor object that instantiated this editor.
		@param executor The executor to use.
	*/

	ScalpelRawEditor(
		String name,
		Boolean editable,
		MontoyaApi API,
		EditorCreationContext creationContext,
		EditorType type,
		ScalpelEditorTabbedPane provider,
		ScalpelExecutor executor
	) {
		super(name, editable, API, creationContext, type, provider, executor);
		try {
			// Create a new editor UI component.
			this.editor = API.userInterface().createRawEditor();

			// Decide wherever the editor must be editable or read only depending on context.
			editor.setEditable(
				editable && creationContext.editorMode() != EditorMode.READ_ONLY
			);
		} catch (Exception e) {
			// Log the error.
			ScalpelLogger.error("Couldn't instantiate new editor:");

			// Log the stack trace.
			ScalpelLogger.logStackTrace(e);

			// Throw the error again.
			throw e;
		}
	}

	protected void setEditorContent(ByteArray bytes) {
		editor.setContents(bytes);
	}

	protected ByteArray getEditorContent() {
		return editor.getContents();
	}

	/**
		Returns the underlying UI component.
		(called by Burp)

		@return The underlying UI component.
	*/
	@Override
	public Component uiComponent() {
		// return new JFileChooser();
		return editor.uiComponent();
	}

	/**
		Returns the selected data.
		(called by Burp)

		@return The selected data.
	*/
	@Override
	public Selection selectedData() {
		return editor.selection().isPresent() ? editor.selection().get() : null;
	}

	/**
		Returns whether the editor has been modified.
		(called by Burp)

		@return Whether the editor has been modified.
	*/
	@Override
	public boolean isModified() {
		return editor.isModified();
	}
}
