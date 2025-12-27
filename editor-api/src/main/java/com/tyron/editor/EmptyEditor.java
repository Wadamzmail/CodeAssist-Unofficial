package com.tyron.editor;

import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class EmptyEditor implements Editor {

  private final File emptyFile = new File("");

  // ---------- BASIC ---------- //

  @Override
  @Nullable
  public Project getProject() {
    return null;
  }

  @Override
  public void setDiagnostics(List<DiagnosticWrapper> diagnostics) {}

  @Override
  public List<DiagnosticWrapper> getDiagnosticsList() {
    return new ArrayList<DiagnosticWrapper>();
  }

  @Override
  public boolean isBackgroundAnalysisEnabled() {
    return false;
  }

  @Override
  public File getCurrentFile() {
    return emptyFile;
  }

  @Override
  public void openFile(File file) {}

  // ---------- POSITION ---------- //

  @Override
  public CharPosition getCharPosition(int index) {
    return new CharPosition(0, 0);
  }

  @Override
  public int getCharIndex(int line, int column) {
    return 0;
  }

  // ---------- SETTINGS ---------- //

  @Override
  public boolean useTab() {
    return false;
  }

  @Override
  public int getTabCount() {
    return 0;
  }

  // ---------- TEXT OPS ---------- //

  @Override
  public void insert(int line, int column, String string) {}

  @Override
  public void insertMultilineString(int line, int column, String string) {}

  @Override
  public void delete(int startLine, int startColumn, int endLine, int endColumn) {}

  @Override
  public void delete(int startIndex, int endIndex) {}

  @Override
  public void replace(int line, int column, int endLine, int endColumn, String string) {}

  // ---------- FORMAT ---------- //

  @Override
  public boolean formatCodeAsync() {
    return false;
  }

  @Override
  public boolean formatCodeAsync(int startIndex, int endIndex) {
    return false;
  }

  // ---------- BATCH ---------- //

  @Override
  public void beginBatchEdit() {}

  @Override
  public void endBatchEdit() {}

  // ---------- CARET / CONTENT ---------- //

  @Override
  public Caret getCaret() {
    return null;
  }

  @Override
  public Content getContent() {
    return null;
  }

  // ---------- SELECTION ---------- //

  @Override
  public void setSelection(int line, int column) {}

  @Override
  public void setSelectionRegion(int line, int column, int endLine, int endColumn) {}

  @Override
  public void setSelectionRegion(int startIndex, int endIndex) {}

  @Override
  public void moveSelectionUp() {}

  @Override
  public void moveSelectionDown() {}

  @Override
  public void moveSelectionLeft() {}

  @Override
  public void moveSelectionRight() {}

  // ---------- STATE ---------- //

  @Override
  public void setAnalyzing(boolean analyzing) {}

  @Override
  public void requireCompletion() {}
}
