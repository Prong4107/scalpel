package lexfo.scalpel.editors;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import lexfo.scalpel.EditorType;
import lexfo.scalpel.ScalpelEditorTabbedPane;
import lexfo.scalpel.ScalpelExecutor;
import org.exbin.bined.CodeType;

public class ScalpelBinaryEditor extends ScalpelGenericBinaryEditor {

	public ScalpelBinaryEditor(
		String name,
		Boolean editable,
		MontoyaApi API,
		EditorCreationContext creationContext,
		EditorType type,
		ScalpelEditorTabbedPane provider,
		ScalpelExecutor executor
	) {
		super(
			name,
			editable,
			API,
			creationContext,
			type,
			provider,
			executor,
			CodeType.BINARY
		);
		this.editor.setMaxBytesPerRow(8);
	}
}
