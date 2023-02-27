package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Optional;
import java.util.stream.Collectors;

class ScalpelEditorProvider
  implements HttpRequestEditorProvider, HttpResponseEditorProvider {

  private final MontoyaApi API;
  private final Logging logger;
  private final LinkedList<WeakReference<ScalpelProvidedEditor>> requestEditorList;
  private final LinkedList<WeakReference<ScalpelProvidedEditor>> responseEditorList;
  private final ScalpelExecutor executor;

  ScalpelEditorProvider(MontoyaApi API, ScalpelExecutor executor) {
    this.API = API;
    this.requestEditorList = new LinkedList<>();
    this.responseEditorList = new LinkedList<>();
    this.logger = API.logging();
    this.executor = executor;
  }

  public Optional<ScalpelProvidedEditor> getDisplayedRequestEditor() {
    return getRequestEditors()
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
  private static void gc() {
    Object obj = new Object();
    WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    while (ref.get() != null) System.gc();
  }

  private static <T> LinkedList<T> filterGarbageReferences(
    LinkedList<WeakReference<T>> weakRefList
  ) {
    // Force garbage collection to turn garbage refs into null
    gc();

    // Filter null references
    return weakRefList
      .parallelStream()
      .map(e -> e.get())
      .filter(e -> e != null)
      .collect(Collectors.toCollection(LinkedList::new));
  }

  public LinkedList<ScalpelProvidedEditor> getRequestEditors() {
    return filterGarbageReferences(requestEditorList);
  }

  public LinkedList<ScalpelProvidedEditor> getResponseEditors() {
    return filterGarbageReferences(responseEditorList);
  }

  @Override
  public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(
    EditorCreationContext creationContext
  ) {
    ScalpelProvidedEditor editor = new ScalpelProvidedEditor(
      API,
      creationContext,
      EditorType.REQUEST,
      this
    );
    requestEditorList.add(new WeakReference<ScalpelProvidedEditor>(editor));
    return editor;
  }

  @Override
  public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(
    EditorCreationContext creationContext
  ) {
    ScalpelProvidedEditor editor = new ScalpelProvidedEditor(
      API,
      creationContext,
      EditorType.RESPONSE,
      this
    );
    responseEditorList.add(new WeakReference<ScalpelProvidedEditor>(editor));
    return editor;
  }
}
