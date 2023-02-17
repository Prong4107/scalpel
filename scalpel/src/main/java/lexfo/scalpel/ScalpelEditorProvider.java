package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Optional;
import java.util.stream.Collectors;

class ScalpelEditorProvider implements HttpRequestEditorProvider {

  private final MontoyaApi API;
  private final LinkedList<WeakReference<ScalpelProvidedEditor>> editorList;

  ScalpelEditorProvider(MontoyaApi API) {
    this.API = API;
    this.editorList = new LinkedList<>();
  }

  public final Optional<ScalpelProvidedEditor> getDisplayedEditor() {
    return getEditors()
      .stream()
      .filter(e -> e.uiComponent().isShowing())
      .findAny();
  }

  /**
   * This method guarantees that garbage collection is
   * done unlike <code>{@link System#gc()}</code>
   *
   */
  // https://stackoverflow.com/a/6915221
  public static void gc() {
    Object obj = new Object();
    WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    while (ref.get() != null) {
      System.gc();
    }
  }

  public LinkedList<ScalpelProvidedEditor> getEditors() {
    // Force garbage collection to be able to filter dead tabs
    gc();

    // Filter trashed references.
    return editorList
      .parallelStream()
      .map(e -> e.get())
      .filter(e -> e != null)
      .collect(Collectors.toCollection(LinkedList::new));
  }

  @Override
  public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(
    EditorCreationContext creationContext
  ) {
    ScalpelProvidedEditor editor = new ScalpelProvidedEditor(
      API,
      creationContext
    );
    editorList.add(new WeakReference<ScalpelProvidedEditor>(editor));
    return editor;
  }
}
