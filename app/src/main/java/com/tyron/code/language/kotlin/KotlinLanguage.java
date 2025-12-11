package com.tyron.code.language.kotlin;

import android.content.res.AssetManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.language.CompletionItemWrapper;
import com.tyron.completion.model.CompletionItem;
import com.tyron.completion.model.CompletionList;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.text.Content;
import androidx.annotation.Nullable;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import com.tyron.code.language.LanguageManager;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
//new
import com.tyron.kotlin.completion.KotlinEnvironment;
import com.tyron.kotlin.completion.KotlinFile;
import com.tyron.completion.util.CompletionUtils;
import com.tyron.builder.BuildModule;
import java.util.Objects;

public class KotlinLanguage extends EmptyTextMateLanguage implements Language {

  private final Editor mEditor;
 // private final KotlinAnalyzer mAnalyzer;
  public boolean createIdentifiers = false;
  private final TextMateLanguage delegate;
   private static final String GRAMMAR_NAME = "kotlin.tmLanguage";
    private static final String LANGUAGE_PATH = "textmate/kotlin/syntaxes/kotlin.tmLanguage";
    private static final String CONFIG_PATH = "textmate/kotlin/language-configuration.json";
  private static final String SCOPENAME ="source.kotlin";
  private KotlinEnvironment kotlinEnvironment;
  
  private final Formatter formatter = new AsyncFormatter() {
        @Nullable
        @Override
        public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
             String formatted;
        try {
            formatted = com.facebook.ktfmt.format.Formatter.format(text.toString(),false);
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
        public TextRange formatRegionAsync(@NonNull Content text,
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

  public KotlinLanguage(Editor editor) {
    mEditor = editor;
    AssetManager assetManager = ApplicationLoader.applicationContext.getAssets();
    delegate = LanguageManager.createTextMateLanguage(SCOPENAME);
 //   mAnalyzer = KotlinAnalyzer.create(editor,this);
  }

    @NonNull
    @Override
    public AnalyzeManager getAnalyzeManager() {
        return delegate.getAnalyzeManager();
     // return mAnalyzer;
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

    String identifierPart = CompletionHelper.computePrefix(content, position, CompletionUtils.JAVA_PREDICATE::test);

    KotlinAutoCompleteProvider provider =
                new KotlinAutoCompleteProvider(editor);
        CompletionList completionList = provider.getCompletionList(identifierPart,
                position.getLine(),
                position.getColumn());
        if (completionList == null) {
            return;
        }
        completionList.getItems().stream().map(CompletionItemWrapper::new).forEach(publisher::addItem);
    }

    private KotlinEnvironment getOrCreateKotlinEnvironment() {
        if (kotlinEnvironment == null) {
            kotlinEnvironment =
                    KotlinEnvironment.Companion.with(List.of(Objects.requireNonNull(BuildModule.getAndroidJar()),
                            BuildModule.getLambdaStubs()));
        }
        return kotlinEnvironment;
    }

  public boolean isAutoCompleteChar(char p1) {
    return p1 == '.' || MyCharacter.isJavaIdentifierPart(p1);
  }

  @Override
  public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
    String text = content.getLine(line).substring(0, column);
    //return getIndentAdvance(text);
    return delegate.getIndentAdvance(content,line,column);
  }

  public int getIndentAdvance(String p1) {
    KotlinLexer lexer = new KotlinLexer(CharStreams.fromString(p1));
    Token token;
    int advance = 0;
    while ((token = lexer.nextToken()) != null) {
      if (token.getType() == KotlinLexer.EOF) {
        break;
      }
      if (token.getType() == KotlinLexer.LCURL) {
        advance++;
        /*case RBRACE:
        advance--;
        break;**/
      }
    }
    advance = Math.max(0, advance);
    return advance * 4;
  }

  @Override
  public boolean useTab() {
    return delegate.useTab();
   // return true;
  }

  public CharSequence format(CharSequence text) {

    CharSequence formatted = null;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    com.facebook.ktfmt.cli.Main main =
        new com.facebook.ktfmt.cli.Main(
            new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)),
            new PrintStream(out),
            new PrintStream(err),
            new String[] {"-"});
    int exitCode = main.run();

    formatted = out.toString();

    if (exitCode != 0) {
      formatted = text;
    }

    if (formatted == null) {
      formatted = text;
    }
    return formatted;
  }

  @Override
  public SymbolPairMatch getSymbolPairs() {
    return delegate.getSymbolPairs();
   // return new SymbolPairMatch.DefaultSymbolPairs();
    
  }

  @Override
  public NewlineHandler[] getNewlineHandlers() {
    return handlers;
  }

  @Override
    public void destroy() {
        delegate.destroy();
     // mAnalyzer.destroy();
    }

  private final NewlineHandler[] handlers = new NewlineHandler[] {new BraceHandler()};

  class BraceHandler implements NewlineHandler {

    @Override
    public boolean matchesRequirement(@NonNull Content text,
                                      @NonNull CharPosition position,
                                      @Nullable Styles style) {
        int line = position.line;
        if (line < 0 || line >= text.getLineCount()) return false;

        String before = text.subContent(line, 0, line, position.column).toString();
        String after  = text.subContent(line, position.column, line,
                                        text.getLine(line).length()).toString();
        return before.trim().endsWith("{") && after.trim().startsWith("}");
    }

    @Override
    @NonNull
    public NewlineHandleResult handleNewline(@NonNull Content text,
                                             @NonNull CharPosition position,
                                             @Nullable Styles style,
                                             int tabSize) {
        int line = position.line;
        String before = text.subContent(line, 0, line, position.column).toString();

        int baseIndent   = TextUtils.countLeadingSpaceCount(before, tabSize);
        int bodyIndent   = baseIndent + getIndentAdvance(before);
        int closeIndent  = baseIndent + getIndentAdvance("");

        String bodyLine  = TextUtils.createIndent(bodyIndent,  tabSize, false);
        String closeLine = TextUtils.createIndent(closeIndent, tabSize, false);

        StringBuilder sb = new StringBuilder("\n")
                .append(bodyLine)
                .append('\n')
                .append(closeLine);

        int shiftBack = closeLine.length() + 1;
        return new NewlineHandleResult(sb, shiftBack);
    }
}


  private List<String> listFiles(Path directory, String extension) throws IOException {
    return Files.walk(directory)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(extension))
        .map(Path::toString)
        .collect(Collectors.toList());
  }
}
