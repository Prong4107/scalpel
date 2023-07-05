package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
  Provides a new ScalpelProvidedEditor object for editing HTTP requests or responses.
 <p> Calls Python scripts to initialize the editor and update the requests or responses.
*/
public class ScalpelEditorProvider
	implements HttpRequestEditorProvider, HttpResponseEditorProvider {

	/**
    The MontoyaApi object used to interact with Burp Suite.
	*/
	private final MontoyaApi API;

	/**
    The ScalpelExecutor object used to execute Python scripts.
	*/
	private final ScalpelExecutor executor;

	private LinkedList<WeakReference<ScalpelEditorTabbedPane>> editorsRefs = new LinkedList<>();

	/**
    Constructs a new ScalpelEditorProvider object with the specified MontoyaApi object and ScalpelExecutor object.

    @param API The MontoyaApi object to use.
    @param executor The ScalpelExecutor object to use.
	*/
	public ScalpelEditorProvider(MontoyaApi API, ScalpelExecutor executor) {
		this.API = API;
		this.executor = executor;
	}

	/**
    Provides a new ExtensionProvidedHttpRequestEditor object for editing an HTTP request.

    @param creationContext The EditorCreationContext object containing information about the request editor.
    @return A new ScalpelProvidedEditor object for editing the HTTP request.
	*/
	@Override
	public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(
		EditorCreationContext creationContext
	) {
		final ScalpelEditorTabbedPane editor = new ScalpelEditorTabbedPane(
			API,
			creationContext,
			EditorType.REQUEST,
			this,
			executor
		);
		editorsRefs.add(new WeakReference<>(editor));
		return editor;
	}

	/**
    Provides a new ExtensionProvidedHttpResponseEditor object for editing an HTTP response.

    @param creationContext The EditorCreationContext object containing information about the response editor.
    @return A new ScalpelProvidedEditor object for editing the HTTP response.
	*/
	@Override
	public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(
		EditorCreationContext creationContext
	) {
		final ScalpelEditorTabbedPane editor = new ScalpelEditorTabbedPane(
			API,
			creationContext,
			EditorType.RESPONSE,
			this,
			executor
		);
		editorsRefs.add(new WeakReference<>(editor));
		return editor;
	}

	private void forceGarbageCollection() {
		final WeakReference<Object> ref = new WeakReference<Object>(
			new Object()
		);
		// The above object may now be garbage collected.

		while (ref.get() != null) {
			System.gc();
		}
	}

	public void resetEditors() {
		ScalpelLogger.log(ScalpelLogger.Level.DEBUG, "Resetting editors...");
		// Destroy all unused editors to avoid useless expensive callbacks.
		// TODO: Improve this by using ReferenceQueue
		// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html
		// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html#isEnqueued()
		forceGarbageCollection();

		// Clean the list
		this.editorsRefs =
			this.editorsRefs.stream()
				.filter(weakRef -> weakRef.get() != null)
				.collect(Collectors.toCollection(LinkedList::new));

		this.editorsRefs.stream()
			.map(e -> e.get())
			.forEach(e -> e.recreateEditors());

		ScalpelLogger.log(ScalpelLogger.Level.DEBUG, "Editors reset.");
	}
}
