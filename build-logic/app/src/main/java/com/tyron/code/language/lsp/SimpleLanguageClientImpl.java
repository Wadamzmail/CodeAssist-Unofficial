package com.tyron.code.language.lsp;

import androidx.annotation.Nullable;
import com.tyron.code.ui.editor.EditorContainerFragment;
import com.tyron.completion.lsp.api.ILanguageClient;
import com.tyron.completion.lsp.util.DiagnosticUtil;
import com.tyron.completion.model.DiagnosticItem;
import com.tyron.completion.model.DiagnosticResult;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kotlin.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleLanguageClientImpl implements ILanguageClient {

  public static final int MAX_DIAGNOSTIC_FILES = 10;
  public static final int MAX_DIAGNOSTIC_ITEMS_PER_FILE = 20;
  protected static final Logger LOG = LoggerFactory.getLogger(SimpleLanguageClientImpl.class);
  private static SimpleLanguageClientImpl mInstance;
  private final Map<File, List<DiagnosticItem>> diagnostics = new HashMap<>();
  protected EditorContainerFragment fragment;

  private SimpleLanguageClientImpl(EditorContainerFragment provider) {
    setFragment(provider);
  }

  public void setFragment(EditorContainerFragment provider) {
    this.fragment = provider;
  }

  public static SimpleLanguageClientImpl initialize(EditorContainerFragment provider) {
    if (mInstance != null) {
      throw new IllegalStateException("Client is already initialized");
    }

    mInstance = new SimpleLanguageClientImpl(provider);

    return getInstance();
  }

  public static SimpleLanguageClientImpl getInstance() {
    if (mInstance == null) {
      throw new IllegalStateException("Client not initialized");
    }

    return mInstance;
  }

  public static void shutdown() {
    if (mInstance != null) {
      mInstance.fragment = null;
    }
    mInstance = null;
  }

  public static boolean isInitialized() {
    return mInstance != null;
  }

  @Override
  public void publishDiagnostics(DiagnosticResult result) {
    if (result == DiagnosticResult.NO_UPDATE || !canUseActivity()) {
      // No update is expected
      return;
    }

    boolean error = result == null;
    // activity.handleDiagnosticsResultVisibility(error || result.getDiagnostics().isEmpty());

    if (error) {
      return;
    }

    File file = result.getFile().toFile();
    if (!file.exists() || !file.isFile()) {
      return;
    }

    final var editorView = fragment.getEditorForFile(file);
    if (editorView != null) {
      final var editor = editorView; // editorView.getEditor();
      if (editor != null) {
        final var container = new DiagnosticsContainer();
        try {
          container.addDiagnostics(
              result.getDiagnostics().stream()
                  .map(DiagnosticItem::asDiagnosticRegion)
                  .collect(Collectors.toList()));
        } catch (Throwable err) {
          LOG.error("Unable to map DiagnosticItem to DiagnosticRegion", err);
        }
        editor.setDiagnostics(container);
      }
    }

    diagnostics.put(file, result.getDiagnostics());
    // activity.setDiagnosticsAdapter(newDiagnosticsAdapter());
  }

  @Nullable
  @Override
  public DiagnosticItem getDiagnosticAt(final File file, final int line, final int column) {
    return DiagnosticUtil.binarySearchDiagnostic(this.diagnostics.get(file), line, column);
  }

  private boolean canUseActivity() {
    return fragment != null
        && !fragment.getActivity().isFinishing()
        && !fragment.getActivity().isDestroyed()
        && !fragment.getActivity().getSupportFragmentManager().isDestroyed()
        && !fragment.getActivity().getSupportFragmentManager().isStateSaved();
  }

  private Unit noOp(final Object obj) {
    return Unit.INSTANCE;
  }
}
