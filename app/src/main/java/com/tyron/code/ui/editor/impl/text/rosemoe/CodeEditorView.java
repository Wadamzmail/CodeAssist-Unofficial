package com.tyron.code.ui.editor.impl.text.rosemoe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableSet;
import com.tyron.actions.DataContext;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.DiagnosticTextmateAnalyzer;
import com.tyron.code.language.HighlightUtil;
import com.tyron.code.language.kotlin.KotlinLanguage;
import com.tyron.code.language.kotlin.KotlinLanguage2;
import com.tyron.code.language.xml.LanguageXML;
import com.tyron.code.ui.editor.CodeAssistCompletionAdapter;
import com.tyron.code.ui.editor.CodeAssistCompletionWindow;
import com.tyron.code.ui.editor.EditorViewModel;
import com.tyron.code.ui.editor.IDEEditor;
import com.tyron.code.ui.editor.NoOpTextActionWindow;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.xml.model.XmlCompletionType;
import com.tyron.completion.xml.util.XmlUtils;
import com.tyron.editor.Caret;
import com.tyron.editor.CharPosition;
import com.tyron.editor.Content;
import com.tyron.editor.Editor;
import com.tyron.xml.completion.util.DOMUtils;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora2.text.EditorUtil;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.tools.Diagnostic;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMParser;
import org.jetbrains.kotlin.com.intellij.util.ReflectionUtil;

// import io.github.rosemoe.sora.text.CharPosition;

public class CodeEditorView extends IDEEditor implements Editor {

  private final Set<Character> IGNORED_PAIR_ENDS =
      ImmutableSet.<Character>builder()
          .add(')')
          .add(']')
          .add('"')
          .add('>')
          .add('\'')
          .add(';')
          .build();

  private boolean mIsBackgroundAnalysisEnabled;

  private List<DiagnosticWrapper> mDiagnostics = new ArrayList<>();
  private Consumer<List<DiagnosticWrapper>> mDiagnosticsListener;
  private boolean mStopConv = false;
  // private File mCurrentFile;
  private EditorViewModel mViewModel;

  private final Paint mDiagnosticPaint;
  private CodeAssistCompletionWindow mCompletionWindow;

  public CodeEditorView(Context context) {
    this(DataContext.wrap(context), null);
  }

  public CodeEditorView(Context context, AttributeSet attrs) {
    this(DataContext.wrap(context), attrs, 0);
  }

  public CodeEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
    this(DataContext.wrap(context), attrs, defStyleAttr, 0);
  }

  public CodeEditorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(DataContext.wrap(context), attrs, defStyleAttr, defStyleRes);

    mDiagnosticPaint = new Paint();
    mDiagnosticPaint.setStrokeWidth(getDpUnit() * 2);

    init();
  }

  @Nullable
  @Override
  public Project getProject() {
    return ProjectManager.getInstance().getCurrentProject();
  }

  @Override
  protected void onSelectionChange() {
    if (mDiagnostics == null) return;
    if (!isDiagnosticDetailEnabled()) return;
    if (getEditorLanguage() instanceof KotlinLanguage2) {
      String msg = getCurrentDiagnosticMessage();
      if (msg.equals("")) return;
      getDiagnosticWindow().showDiagnostic(msg);
      return;
    }
    DiagnosticWrapper diagnosticWrapper =
        DiagnosticUtil.getDiagnosticWrapper(
            getEditorLanguage() instanceof KotlinLanguage
                ? ((KotlinLanguage) getEditorLanguage()).getDiagnostics()
                : mDiagnostics,
            getCursor().getLeft(),
            getCursor().getRight());
    if (diagnosticWrapper == null) return;
    getDiagnosticWindow().showDiagnostic(diagnosticWrapper.getMessage(Locale.getDefault()));
  }

  @Override
  public void setEditorLanguage(@Nullable Language lang) {
    super.setEditorLanguage(lang);

    if (lang != null) {
      // languages should have an option to declare their own tab width
      try {
        Class<? extends Language> aClass = lang.getClass();
        Method method = ReflectionUtil.getDeclaredMethod(aClass, "getTabWidth");
        if (method != null) {
          Object invoke = method.invoke(getEditorLanguage());
          if (invoke instanceof Integer) {
            setTabWidth((Integer) invoke);
          }
        }
      } catch (Throwable e) {
        // use default
      }
    }
  }

  private void init() {
    mCompletionWindow = new CodeAssistCompletionWindow(this);
    mCompletionWindow.setAdapter(new CodeAssistCompletionAdapter());
    replaceComponent(EditorAutoCompletion.class, mCompletionWindow);
    replaceComponent(EditorTextActionWindow.class, new NoOpTextActionWindow(this));
    setDiagnostics(new DiagnosticsContainer());
  }

  @Override
  public void setColorScheme(@NonNull EditorColorScheme colors) {
    super.setColorScheme(colors);
  }

  public List<DiagnosticWrapper> getDiagnosticsList() {
    return mDiagnostics;
  }

  @Override
  public void setDiagnostics(List<DiagnosticWrapper> diagnostics) {
    if (diagnostics == null) {
      diagnostics = new ArrayList<DiagnosticWrapper>();
    }
    mStopConv = true;
    mDiagnostics = diagnostics;

    AnalyzeManager manager = getEditorLanguage().getAnalyzeManager();
    if (manager instanceof DiagnosticTextmateAnalyzer) {
      ((DiagnosticTextmateAnalyzer) manager).setDiagnostics(this, diagnostics);
    }

    if (mDiagnosticsListener != null) {
      mDiagnosticsListener.accept(mDiagnostics);
    }

    Styles styles = getStyles();
    if (styles != null) {
      HighlightUtil.clearDiagnostics(styles);
      HighlightUtil.markDiagnostics(this, diagnostics, styles);
      setStyles(styles);
    }
    convDiagnostics(diagnostics);
  }

  public void setDiagnosticsListener(Consumer<List<DiagnosticWrapper>> listener) {
    mDiagnosticsListener = listener;
  }

  @Override
  public File getCurrentFile() {
    return mCurrentFile;
  }

  @Override
  public void openFile(File file) {
    mCurrentFile = file;
  }

  @Override
  public CharPosition getCharPosition(int index) {
    io.github.rosemoe.sora.text.CharPosition charPosition =
        getText().getIndexer().getCharPosition(index);
    return new CharPosition(charPosition.line, charPosition.column);
  }

  @Override
  public int getCharIndex(int line, int column) {
    return getText().getCharIndex(line, column);
  }

  @Override
  public boolean useTab() {
    //noinspection ConstantConditions, editor language can be null
    if (getEditorLanguage() == null) {
      // enabled by default
      return true;
    }

    return getEditorLanguage().useTab();
  }

  @Override
  public int getTabCount() {
    return getTabWidth();
  }

  @Override
  public void insert(int line, int column, String string) {
    getText().insert(line, column, string);
  }

  @Override
  public void commitText(CharSequence text) {
    try {
      super.commitText(text);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void commitText(CharSequence text, boolean applyAutoIndent) {
    try {
      if (text.length() == 1) {
        io.github.rosemoe.sora.text.Content content = getText();
        int cursorIndex = getCursor().getLeft();

        if (cursorIndex < content.length()) {
          char currentChar = content.charAt(cursorIndex);
          char c = text.charAt(0);

          if (IGNORED_PAIR_ENDS.contains(c) && c == currentChar) {
            // ignored pair end, just move the cursor over the character
            setSelection(getCursor().getLeftLine(), getCursor().getLeftColumn() + 1);
            return;
          }
        }
      }
    } catch (Exception e) {
      android.util.Log.e("CodeEditorView", "failed to commit text", e);
    }

    super.commitText(text, applyAutoIndent);

    if (text.length() == 1) {
      char c = text.charAt(0);
      handleAutoInsert(c);
    }
  }

  private void handleAutoInsert(char c) {
    if (getEditorLanguage() instanceof LanguageXML) {
      if (c != '>' && c != '/') {
        return;
      }
      boolean full = c == '>';

      DOMDocument document = DOMParser.getInstance().parse(getText().toString(), "", null);
      DOMNode nodeAt = document.findNodeAt(getCursor().getLeft());
      if (!DOMUtils.isClosed(nodeAt) && nodeAt.getNodeName() != null) {
        if (XmlUtils.getCompletionType(document, getCursor().getLeft())
            == XmlCompletionType.ATTRIBUTE_VALUE) {
          return;
        }
        String insertText = full ? "</" + nodeAt.getNodeName() + ">" : ">";
        commitText(insertText);
        setSelection(
            getCursor().getLeftLine(),
            getCursor().getLeftColumn() - (full ? insertText.length() : 0));
      }
    }
  }

  @Override
  public void deleteText() {
    try {
      Cursor cursor = getCursor();
      if (!cursor.isSelected()) {
        io.github.rosemoe.sora.text.Content text = getText();
        int startIndex = cursor.getLeft();
        int length = text.length();
        if (startIndex - 1 >= 0) {
          char deleteChar = text.charAt(startIndex - 1);
          if (startIndex < length) {
            char afterChar = text.charAt(startIndex);
            SymbolPairMatch.SymbolPair replacement = null;

            SymbolPairMatch pairs = getEditorLanguage().getSymbolPairs();
            if (pairs != null) {
              replacement = pairs.matchBestPairBySingleChar(deleteChar);
            }
            if (replacement != null) {
              if (("" + deleteChar + afterChar).equals(replacement.open + replacement.close)) {
                text.delete(startIndex - 1, startIndex + 1);
                return;
              }
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    super.deleteText();
  }

  @Override
  public void insertMultilineString(int line, int column, String string) {
    String currentLine = getText().getLineString(line);

    String[] lines = string.split("\\n");
    if (lines.length == 0) {
      return;
    }
    int count = TextUtils.countLeadingSpaceCount(currentLine, getTabWidth());
    for (int i = 0; i < lines.length; i++) {
      String trimmed = lines[i].trim();

      int advance = EditorUtil.getFormatIndent(getEditorLanguage(), trimmed);

      if (advance < 0) {
        count += advance;
      }

      if (i != 0) {
        String indent = TextUtils.createIndent(count, getTabWidth(), useTab());
        trimmed = indent + trimmed;
      }

      lines[i] = trimmed;

      if (advance > 0) {
        count += advance;
      }
    }

    String textToInsert = String.join("\n", lines);
    getText().insert(line, column, textToInsert);
  }

  @Override
  public void delete(int startLine, int startColumn, int endLine, int endColumn) {
    getText().delete(startLine, startColumn, endLine, endColumn);
  }

  @Override
  public void delete(int startIndex, int endIndex) {
    getText().delete(startIndex, endIndex);
  }

  @Override
  public void replace(int line, int column, int endLine, int endColumn, String string) {
    getText().replace(line, column, endLine, endColumn, string);
  }

  @Override
  public void setSelection(int line, int column) {
    super.setSelection(line, column);
  }

  @Override
  public void setSelectionRegion(int lineLeft, int columnLeft, int lineRight, int columnRight) {
    CodeEditorView.super.setSelectionRegion(lineLeft, columnLeft, lineRight, columnRight);
  }

  @Override
  public void setSelectionRegion(int startIndex, int endIndex) {
    CharPosition start = getCharPosition(startIndex);
    CharPosition end = getCharPosition(endIndex);
    CodeEditorView.super.setSelectionRegion(
        start.getLine(), start.getColumn(), end.getLine(), end.getColumn());
  }

  @Override
  public void beginBatchEdit() {
    getText().beginBatchEdit();
  }

  @Override
  public void endBatchEdit() {
    getText().endBatchEdit();
  }

  @Override
  public synchronized boolean formatCodeAsync() {
    return CodeEditorView.super.formatCodeAsync();
  }

  @Override
  public synchronized boolean formatCodeAsync(int start, int end) {
    return formatCodeAsync(
        getText().getIndexer().getCharPosition(start), getText().getIndexer().getCharPosition(end));
  }

  @Override
  public Caret getCaret() {
    return new CursorWrapper(getCursor());
  }

  @Override
  public Content getContent() {
    return new ContentWrapper(CodeEditorView.this.getText());
  }

  /**
   * Background analysis can sometimes be expensive. Set whether background analysis should be
   * enabled for this editor.
   */
  public void setBackgroundAnalysisEnabled(boolean enabled) {
    mIsBackgroundAnalysisEnabled = enabled;
  }

  @Override
  public void rerunAnalysis() {
    //noinspection ConstantConditions
    if (getEditorLanguage() != null) {
      AnalyzeManager analyzeManager = getEditorLanguage().getAnalyzeManager();
      Project project = ProjectManager.getInstance().getCurrentProject();

      if (analyzeManager instanceof DiagnosticTextmateAnalyzer) {
        if (isBackgroundAnalysisEnabled() && (project != null && !project.isCompiling())) {
          ((DiagnosticTextmateAnalyzer) analyzeManager).rerunWithBg();
        } else {
          ((DiagnosticTextmateAnalyzer) analyzeManager).rerunWithoutBg();
        }
      } else {
        analyzeManager.rerun();
      }
    }
  }

  @Override
  public boolean isBackgroundAnalysisEnabled() {
    return mIsBackgroundAnalysisEnabled;
  }

  public void setAnalyzing(boolean analyzing) {
    if (mViewModel != null) {
      mViewModel.setAnalyzeState(analyzing);
    }
  }

  @Override
  public void requireCompletion() {
    mCompletionWindow.requireCompletion();
  }

  public void setViewModel(EditorViewModel editorViewModel) {
    mViewModel = editorViewModel;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
  }

  @Override
  public void moveSelectionRight() {}

  @Override
  public void moveSelectionUp() {}

  @Override
  public void moveSelectionDown() {}

  @Override
  public void moveSelectionLeft() {}

  private void drawSquigglyLine(Canvas canvas, float startX, float startY, float endX, float endY) {
    float waveSize = getDpUnit() * 3;
    float doubleWaveSize = waveSize * 2;
    float width = endX - startX;
    for (int i = (int) startX; i < startX + width; i += doubleWaveSize) {
      canvas.drawLine(i, startY, i + waveSize, startY - waveSize, mDiagnosticPaint);
      canvas.drawLine(
          i + waveSize, startY - waveSize, i + doubleWaveSize, startY, mDiagnosticPaint);
    }
  }

  private void setDiagnosticColor(DiagnosticWrapper wrapper) {
    EditorColorScheme color = getColorScheme();
    switch (wrapper.getKind()) {
      case ERROR:
        mDiagnosticPaint.setColor(color.getColor(EditorColorScheme.PROBLEM_ERROR));
        break;
      case MANDATORY_WARNING:
      case WARNING:
        mDiagnosticPaint.setColor(color.getColor(EditorColorScheme.PROBLEM_WARNING));
    }
  }

  private boolean isDiagnosticDetailEnabled() {
    return ApplicationLoader.getDefaultPreferences()
        .getBoolean(SharedPreferenceKeys.DIAGNOSTIC_DETAIL, false);
  }

  private void convDiagnostics(List<? extends Diagnostic<?>> diagnostics) {

    Function<Diagnostic.Kind, Short> severitySupplier =
        it -> {
          switch (it) {
            case ERROR:
              return DiagnosticRegion.SEVERITY_ERROR;
            case MANDATORY_WARNING:
            case WARNING:
              return DiagnosticRegion.SEVERITY_WARNING;
            default:
            case OTHER:
            case NOTE:
              return DiagnosticRegion.SEVERITY_NONE;
          }
        };
    if (getDiagnostics() == null) setDiagnostics(new DiagnosticsContainer());
    getDiagnostics().reset();
    mStopConv = false;

    for (var it : diagnostics) {
      if (mStopConv) break;
      DiagnosticRegion region =
          new DiagnosticRegion(
              (int) it.getStartPosition(),
              (int) it.getEndPosition(),
              severitySupplier.apply(it.getKind()),
              0,
              null);
      // isDiagnosticDetailEnabled()
      //    ? new DiagnosticDetail("Info", it.getMessage(Locale.getDefault()), null, null)
      //    : null);
      getDiagnostics().addDiagnostic(region);
    }
  }
}
