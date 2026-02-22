package com.tyron.code.language.lsp;

import androidx.annotation.Nullable;
import com.tyron.completion.model.CodeActionItem;
import com.tyron.completion.model.DiagnosticItem;
import com.tyron.completion.model.DiagnosticResult;
import com.tyron.completion.model.PerformCodeActionParams;
import com.tyron.completion.model.document.ShowDocumentParams;
import com.tyron.completion.model.document.ShowDocumentResult;
import com.tyron.completion.model.location.Location;
import com.tyron.completion.lsp.api.ILanguageClient;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.text.Content;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import kotlin.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tyron.code.ui.editor.EditorContainerFragment;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.completion.util.DiagnosticUtil;

public class SimpleLanguageClientImpl extends ILanguageClient {
  
  public static final int MAX_DIAGNOSTIC_FILES = 10;
  public static final int MAX_DIAGNOSTIC_ITEMS_PER_FILE = 20;
  protected static final Logger LOG = LoggerFactory.getLogger(SimpleLanguageClientImpl.class);
  private static SimpleLanguageClientImpl mInstance;
  private final Map<File, List<DiagnosticItem>> diagnostics = new HashMap<>();
  protected EditorContainerFragment fragment;
  
  private SimpleLanguageClientImpl(EditorContainerFragment provider) {
    setActivity(provider);
  }

  public void setFragment(EditorContainerFragment provider) {
    this.activity = provider;
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
      final var editor = editorView; //editorView.getEditor();
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
        && !fragment.getAactivity().getSupportFragmentManager().isDestroyed()
        && !fragment.getActivity().getSupportFragmentManager().isStateSaved();
  }

  private Unit noOp(final Object obj) {
    return Unit.INSTANCE;
  }
}