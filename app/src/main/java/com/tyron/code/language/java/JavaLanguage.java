package com.tyron.code.language.java;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tyron.builder.project.Project;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.language.EditorFormatter;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
// new
import com.tyron.completion.CompletionParameters;
import com.tyron.completion.java.JavaCompletionProvider;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.SignatureProvider;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.model.signatures.SignatureHelp;
import com.tyron.completion.model.signatures.SignatureHelpLanguage;
import com.tyron.completion.model.signatures.SignatureHelpLanguageKt;
import com.tyron.completion.model.signatures.SignatureHelpParams;
import com.tyron.editor.Editor;
import com.tyron.language.api.CodeAssistLanguage;
import io.github.rosemoe.editor.langs.java.JavaTextTokenizer;
import io.github.rosemoe.editor.langs.java.Tokens;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import java.io.File;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaLanguage extends EmptyTextMateLanguage
    implements Language, EditorFormatter, CodeAssistLanguage, SignatureHelpLanguage {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaLanguage.class);

  private final Editor mEditor;

  private final BaseTextmateAnalyzer mAnalyzer;
  //  private final TextMateLanguage delegate;
  public boolean createIdentifiers = false;
  private static final String GRAMMAR_NAME = "java.tmLanguage.json";
  private static final String LANGUAGE_PATH = "textmate/java/syntaxes/java.tmLanguage.json";
  private static final String CONFIG_PATH = "textmate/java/language-configuration.json";
  private static final String SCOPENAME = "source.java";
  private final Formatter formatter =
      new AsyncFormatter() {
        @Nullable
        @Override
        public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
          String formatted;
          try {
            formatted =
                new com.google.googlejavaformat.java.Formatter().formatSource(text.toString());
          } catch (Exception e) {
            formatted = text.toString(); // fallback
          }

          if (!text.toString().equals(formatted)) {
            int oldCursor = cursorRange.getStartIndex();
            text.delete(0, text.length());
            text.insert(0, 0, formatted);
            int newCursor = Math.min(oldCursor, formatted.length());
            CharPosition pos = text.getIndexer().getCharPosition(newCursor);
            return new TextRange(pos, pos);
          }

          return cursorRange;
        }

        @Nullable
        @Override
        public TextRange formatRegionAsync(
            @NonNull Content text,
            @NonNull TextRange rangeToFormat,
            @NonNull TextRange cursorRange) {
          return null;
        }
      };

  @NonNull
  @Override
  public Formatter getFormatter() {
    return formatter;
  }

  @Override
  public SignatureHelp signatureHelp(SignatureHelpParams params) {
    var signatureHelp = SignatureHelpLanguageKt.unsupportedSignatureHelp();
    if (!com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
      var file = mEditor.getCurrentFile();
      var project = mEditor.getProject();
      var module = project.getModule(file);
      signatureHelp =
          new SignatureProvider(module, params.getCancelChecker()).signatureHelp(params);
    }
    return signatureHelp;
  }

  public JavaLanguage(Editor editor) {
    this.mEditor = editor;
    //   delegate = LanguageManager.createTextMateLanguage(SCOPENAME);
    mAnalyzer = JavaAnalyzer.create(editor, this);
    // onContentChange(editor.getCurrentFile(),editor.getContent());
  }

  public boolean isAutoCompleteChar(char p1) {
    return p1 == '.' || MyCharacter.isJavaIdentifierPart(p1);
  }

  public int getIndentAdvance(String p1) {
    JavaTextTokenizer tokenizer = new JavaTextTokenizer(p1);
    Tokens token;
    int advance = 0;
    while ((token = tokenizer.directNextToken()) != Tokens.EOF) {
      switch (token) {
        case LBRACE:
          advance++;
          break;
      }
    }
    return (advance * getTabWidth());
  }

  public int getFormatIndent(String line) {
    JavaTextTokenizer tokenizer = new JavaTextTokenizer(line);
    Tokens token;
    int advance = 0;
    while ((token = tokenizer.directNextToken()) != Tokens.EOF) {
      switch (token) {
        case LBRACE:
          advance++;
          break;
        case RBRACE:
          advance--;
      }
    }
    return (advance * getTabWidth());
  }

  @NonNull
  @Override
  public AnalyzeManager getAnalyzeManager() {
    //    return delegate.getAnalyzeManager();
    return mAnalyzer;
  }

  @Override
  public int getInterruptionLevel() {
    return INTERRUPTION_LEVEL_SLIGHT;
  }

  @Override
  public void requireAutoComplete(
      @NonNull ContentReference content,
      @NonNull CharPosition position,
      @NonNull CompletionPublisher publisher,
      @NonNull Bundle extraArguments)
      throws CompletionCancelledException {
    char c = content.charAt(position.getIndex() - 1);
    if (!isAutoCompleteChar(c)) {
      return;
    }
    String prefix =
        CompletionHelper.computePrefix(content, position, MyCharacter::isJavaIdentifierPart);
    CompletionParameters parameters =
        CompletionParameters.builder()
            .setColumn(position.getColumn())
            .setLine(position.getLine())
            .setIndex(position.getIndex())
            .setEditor(mEditor)
            .setFile(mEditor.getCurrentFile())
            .setProject(mEditor.getProject())
            .setModule(mEditor.getProject().getMainModule())
            .setContents(content.getReference().toString())
            .setPrefix(prefix)
            .build();
    JavaCompletionProvider provider = new JavaCompletionProvider();
    CompletionList list = provider.complete(parameters);

    publisher.setUpdateThreshold(0);
    publisher.addItems(
        list.getItems().stream() /*.map(CompletionItemWrapper::new)*/.collect(Collectors.toList()));
  }

  @Override
  public void onContentChange(File file, CharSequence content) {
    if (true) return;
    Project project = mEditor.getProject();
    if (project == null) {
      return;
    }
    if (com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
      return;
    }
    CompilationInfo compilationInfo = CompilationInfo.get(project, mEditor.getCurrentFile());
    if (compilationInfo == null) {
      return;
    }
    JavaFileObject fileObject =
        new SimpleJavaFileObject(mEditor.getCurrentFile().toURI(), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
          }
        };
    try {
      compilationInfo.update(fileObject);
    } catch (Throwable t) {
      LOGGER.error("Failed to update compilation unit", t);
    }
  }

  @Override
  public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
    String text = content.getLine(line).substring(0, column);
    return getIndentAdvance(text);
  }

  @Override
  public boolean useTab() {
    return true;
  }

  public int getTabWidth() {
    return 4;
  }

  public CharSequence format(CharSequence p1) {
    return format(p1, 0, p1.length());
  }

  @NonNull
  @Override
  public CharSequence format(@NonNull CharSequence text, int start, int end) {

    CharSequence formatted = null;
    try {
      formatted = new com.google.googlejavaformat.java.Formatter().formatSource(text.toString());
    } catch (Exception e) {
      formatted = text;
    }

    if (formatted == null) {
      formatted = text;
    }
    return formatted;
  }

  @Override
  public SymbolPairMatch getSymbolPairs() {
    //    return delegate.getSymbolPairs();
    return new SymbolPairMatch.DefaultSymbolPairs();
  }

  private final NewlineHandler[] newLineHandlers =
      new NewlineHandler[] {
        new BraceHandler(), new TwoIndentHandler(), new JavaDocStartHandler(), new JavaDocHandler()
      };

  @Override
  public NewlineHandler[] getNewlineHandlers() {
    return newLineHandlers;
  }

  @Override
  public void destroy() {
    //    delegate.destroy();
    mAnalyzer.destroy();
  }

  class CallParenHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(
        @NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {
      int line = position.line;
      if (line < 0 || line >= text.getLineCount()) return false;

      String before = text.subContent(line, 0, line, position.column).toString();

      String after =
          text.subContent(line, position.column, line, text.getLine(line).length()).toString();

      return before.replace("\r", "").trim().startsWith(".") == false
          && before.trim().endsWith(")")
          && !after.trim().startsWith(";");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(
        @NonNull Content text,
        @NonNull CharPosition position,
        @Nullable Styles style,
        int tabSize) {
      int line = position.line;
      String before = text.subContent(line, 0, line, position.column).toString();

      int indent = TextUtils.countLeadingSpaceCount(before, tabSize);
      int advance = getIndentAdvance(before) + 8; // +8 = 4*2
      String indentStr = TextUtils.createIndent(indent + advance, tabSize, false);

      return new NewlineHandleResult(new StringBuilder("\n").append(indentStr), 0);
    }
  }

  final class TwoIndentHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(
        @NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {
      int line = position.line;
      if (line < 0 || line >= text.getLineCount()) return false;

      String before = text.subContent(line, 0, line, position.column).toString();
      String after =
          text.subContent(line, position.column, line, text.getLine(line).length()).toString();

      return !before.replace("\r", "").trim().startsWith(".")
          && before.trim().endsWith(")")
          && !after.trim().startsWith(";");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(
        @NonNull Content text,
        @NonNull CharPosition position,
        @Nullable Styles style,
        int tabSize) {
      int line = position.line;
      String before = text.subContent(line, 0, line, position.column).toString();

      int indent = TextUtils.countLeadingSpaceCount(before, tabSize);
      int extraIndent = 8; // 4 * 2
      String indentStr = TextUtils.createIndent(indent + extraIndent, tabSize, false);

      return new NewlineHandleResult(new StringBuilder("\n").append(indentStr), 0);
    }
  }

  class BraceHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(
        @NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {
      int line = position.line;
      if (line < 0 || line >= text.getLineCount()) return false;

      String before = text.subContent(line, 0, line, position.column).toString();

      String after =
          text.subContent(line, position.column, line, text.getLine(line).length()).toString();

      return before.trim().endsWith("{") && after.trim().startsWith("}");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(
        @NonNull Content text,
        @NonNull CharPosition position,
        @Nullable Styles style,
        int tabSize) {
      int line = position.line;

      String before = text.subContent(line, 0, line, position.column).toString();

      String after =
          text.subContent(line, position.column, line, text.getLine(line).length()).toString();

      int indentBase = TextUtils.countLeadingSpaceCount(before, tabSize);
      int advanceBefore = getIndentAdvance(before); // or your helper
      int advanceAfter = getIndentAdvance(after);

      String indent = TextUtils.createIndent(indentBase + advanceBefore, tabSize, false);
      String closingIndent = TextUtils.createIndent(indentBase + advanceAfter, tabSize, false);

      StringBuilder sb = new StringBuilder("\n").append(indent).append('\n').append(closingIndent);

      int cursorShiftBack = closingIndent.length() + 1;
      return new NewlineHandleResult(sb, cursorShiftBack);
    }
  }

  class JavaDocStartHandler implements NewlineHandler {

    private final boolean shouldCreateEnd = true;

    @Override
    public boolean matchesRequirement(
        @NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {
      int line = position.line;
      if (line < 0 || line >= text.getLineCount()) return false;

      String before = text.subContent(line, 0, line, position.column).toString();

      return before.trim().startsWith("/**");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(
        @NonNull Content text,
        @NonNull CharPosition position,
        @Nullable Styles style,
        int tabSize) {
      int line = position.line;
      String before = text.subContent(line, 0, line, position.column).toString();

      int indent = TextUtils.countLeadingSpaceCount(before, tabSize);

      StringBuilder sb =
          new StringBuilder("\n")
              .append(TextUtils.createIndent(indent, tabSize, false))
              .append(" * ");

      if (shouldCreateEnd) {
        String endIndent = TextUtils.createIndent(indent, tabSize, false);
        sb.append("\n").append(endIndent).append(" */");
        int cursorShift = endIndent.length() + 4; // back-up to " * "
        return new NewlineHandleResult(sb, cursorShift);
      }

      return new NewlineHandleResult(sb, 0);
    }
  }

  class JavaDocHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(
        @NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {
      int line = position.line;
      if (line < 0 || line >= text.getLineCount()) return false;

      String before = text.subContent(line, 0, line, position.column).toString();

      String trimmed = before.trim();
      return trimmed.startsWith("*") && !trimmed.startsWith("*/");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(
        @NonNull Content text,
        @NonNull CharPosition position,
        @Nullable Styles style,
        int tabSize) {
      int line = position.line;
      String before = text.subContent(line, 0, line, position.column).toString();

      int indent = TextUtils.countLeadingSpaceCount(before, tabSize);
      StringBuilder sb =
          new StringBuilder("\n")
              .append(TextUtils.createIndent(indent, tabSize, false))
              .append("* ");
      return new NewlineHandleResult(sb, 0);
    }
  }
}
