package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import java.awt.*;
import java.io.IOException;
import java.util.Optional;
import org.exbin.auxiliary.paged_data.BinaryData;
import org.exbin.auxiliary.paged_data.ByteArrayEditableData;
import org.exbin.bined.EditMode;
import org.exbin.bined.SelectionRange;
import org.exbin.bined.swing.basic.CodeArea;

// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpRequestEditor.html
// https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/ui/editor/extension/ExtensionProvidedHttpResponseEditor.html
/**
  Hexadecimal editor implementation for a Scalpel editor
  Users can press their keyboard's INSER key to enter insertion mode
  (which is impossible in Burp's native hex editor)
*/
public class ScalpelHexEditor extends AbstractEditor {

	private final CodeArea editor;

	private BinaryData oldContent = null;

	/**
		Constructs a new Scalpel editor.
		
		@param API The Montoya API object.
		@param creationContext The EditorCreationContext object containing information about the editor.
		@param type The editor type (REQUEST or RESPONSE).
		@param provider The ScalpelProvidedEditor object that instantiated this editor.
		@param executor The executor to use.
	*/
	ScalpelHexEditor(
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
			// Create the base BinEd editor component.
			this.editor = new CodeArea();

			// Decide wherever the editor must be editable or read only depending on context.
			final boolean isEditable =
				editable &&
				creationContext.editorMode() != EditorMode.READ_ONLY;

			// EXPANDING means that editing the content and modifying the content in a way that increases it's total size are allowed.
			// (as opposed to INPLACE or CAPPED where exceeding data is removed)
			final var editMode = isEditable
				? EditMode.EXPANDING
				: EditMode.READ_ONLY;

			editor.setEditMode(editMode);
		} catch (Exception e) {
			// Log the error.
			ScalpelLogger.error("Couldn't instantiate new editor:");

			// Log the stack trace.
			ScalpelLogger.logStackTrace(e);

			// Throw the error again.
			throw e;
		}
	}

	/**
	 * Convert from Burp format to BinEd format
	 * @param binaryData Bytes as Burp format
	 * @return Bytes as BinEd format
	 */
	private BinaryData byteArrayToBinaryData(ByteArray byteArray) {
		final byte[] bytes = byteArray.getBytes();
		final BinaryData result = new ByteArrayEditableData(bytes);
		return result;
	}

	/**
	 * Convert from BinEd format to Burp format
	 * @param binaryData Bytes as BinEd format
	 * @return Bytes as Burp format
	 */
	private ByteArray binaryDataToByteArray(BinaryData binaryData) {
		// Load the data
		final ByteArrayEditableData buffer = new ByteArrayEditableData();
		try {
			buffer.loadFromStream(binaryData.getDataInputStream());
		} catch (IOException ex) {
			throw new RuntimeException(
				"Unexpected error happened while loading bytes from hex editor"
			);
		}

		final byte[] bytes = buffer.getData();

		// Convert bytes to Burp ByteArray
		final ByteArray result = ByteArray.byteArray(bytes);
		return result;
	}

	protected void setEditorContent(ByteArray bytes) {
		// Convert from burp format to BinEd format
		final var newContent = byteArrayToBinaryData(bytes);
		editor.setContentData(newContent);

		// Keep the old content for isModified()
		oldContent = newContent;
	}

	protected ByteArray getEditorContent() {
		// Convert BinEd format to Burp format
		return binaryDataToByteArray(editor.getContentData());
	}

	/**
		Returns the underlying UI component.
		(called by Burp)

		@return The underlying UI component.
	*/
	@Override
	public Component uiComponent() {
		return editor;
	}

	/**
		Returns the selected data.
		(called by Burp)

		@return The selected data.
	*/
	@Override
	public Selection selectedData() {
		final SelectionRange selected = editor.getSelection();

		// Convert BinEd selection range to Burp selection range
		Selection burpSelection = Selection.selection(
			(int) selected.getStart(),
			(int) selected.getEnd()
		);
		return burpSelection;
	}

	/**
		Returns whether the editor has been modified.
		(called by Burp)

		@return Whether the editor has been modified.
	*/
	@Override
	public boolean isModified() {
		// Check if current content is the same as the provided data.
		return Optional
			.ofNullable(editor.getContentData())
			.map(c -> c.equals(oldContent))
			.orElse(false);
	}
}
