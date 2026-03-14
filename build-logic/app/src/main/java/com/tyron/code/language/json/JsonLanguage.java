package com.tyron.code.language.json;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.text.TextUtils;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import java.io.StringWriter;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

public class JsonLanguage extends EmptyTextMateLanguage implements Language {

  private final Editor mEditor;
  public String SCOPENAME = "source.json";

  private final BaseTextmateAnalyzer mAnalyzer;
  private final Formatter formatter =
      new AsyncFormatter() {
        @Nullable
        @Override
        public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
          String formatted = "";
          try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (StringWriter writer = new StringWriter()) {
              JsonWriter jsonWriter = gson.newJsonWriter(writer);
              jsonWriter.setIndent(useTab() ? "\t" : " ");
              gson.toJson(JsonParser.parseString(text.toString()), jsonWriter);
              formatted = writer.toString();
            }
          } catch (Throwable e) {
            // format error, return the original string
            formatted = text.toString();
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

  public JsonLanguage(Editor editor) {
    mEditor = editor;

    try {
      AssetManager assetManager = ApplicationLoader.applicationContext.getAssets();
      mAnalyzer =
          new BaseTextmateAnalyzer(
              this,
              GrammarRegistry.getInstance().findGrammar(SCOPENAME),
              GrammarRegistry.getInstance().findLanguageConfiguration(SCOPENAME),
              ThemeRegistry.getInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NonNull
  @Override
  public AnalyzeManager getAnalyzeManager() {
    return mAnalyzer;
  }

  @Override
  public int getInterruptionLevel() {
    return INTERRUPTION_LEVEL_STRONG;
  }

  @Override
  public void requireAutoComplete(
      @NonNull ContentReference content,
      @NonNull CharPosition position,
      @NonNull CompletionPublisher publisher,
      @NonNull Bundle extraArguments)
      throws CompletionCancelledException {}

  public int getTabWidth() {
    return 2;
  }

  @Override
  public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
    String text = content.getLine(line).substring(0, column);
    return getIndentAdvance(text);
  }

  private int getIndentAdvance(String content) {
    JSONLexer lexer = new JSONLexer(CharStreams.fromString(content));
    Token token;
    int advance = 0;
    while ((token = lexer.nextToken()).getType() != Token.EOF) {
      int type = token.getType();
      if (type == JSONLexer.LBRACKET || type == JSONLexer.LBRACE) {
        advance++;
      }
    }
    advance = Math.max(0, advance);
    return advance * getTabWidth();
  }

  @Override
  public boolean useTab() {
    return true;
  }

  @SuppressLint("WrongThread")
  public CharSequence format(CharSequence text) {
    try {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      try (StringWriter writer = new StringWriter()) {
        JsonWriter jsonWriter = gson.newJsonWriter(writer);
        jsonWriter.setIndent(useTab() ? "\t" : " ");
        gson.toJson(JsonParser.parseString(text.toString()), jsonWriter);
        return writer.toString();
      }
    } catch (Throwable e) {
      // format error, return the original string
      return text;
    }
  }

  @Override
  public SymbolPairMatch getSymbolPairs() {
    return new SymbolPairMatch.DefaultSymbolPairs();
  }

  @Override
  public NewlineHandler[] getNewlineHandlers() {
    return new NewlineHandler[] {new IndentHandler("{", "}"), new IndentHandler("[", "]")};
  }

  @Override
  public void destroy() {
    mAnalyzer.destroy();
  }

  /* class IndentHandler implements NewlineHandler {

      private final String start;
      private final String end;

      public IndentHandler(String start, String end) {
        this.start = start;
        this.end = end;
      }

      @Override
      public boolean matchesRequirement(String beforeText, String afterText) {
        return beforeText.endsWith(start) && afterText.startsWith(end);
      }

      @Override
      public NewlineHandleResult handleNewline(String beforeText, String afterText, int tabSize) {
        int count = TextUtils.countLeadingSpaceCount(beforeText, tabSize);
        int advanceBefore = getIndentAdvance(beforeText);
        int advanceAfter = getIndentAdvance(afterText);
        String text;
        StringBuilder sb =
            new StringBuilder("\n")
                .append(TextUtils.createIndent(count + advanceBefore, tabSize, useTab()))
                .append('\n')
                .append(text = TextUtils.createIndent(count + advanceAfter, tabSize, useTab()));
        int shiftLeft = text.length() + 1;
        return new NewlineHandleResult(sb, shiftLeft);
      }

    }
  }

    }*/
  class IndentHandler implements NewlineHandler {

    private final String start;
    private final String end;

    IndentHandler(String start, String end) {
      this.start = start;
      this.end = end;
    }

    @Override
    public boolean matchesRequirement(
        @NonNull Content text, @NonNull CharPosition position, @Nullable Styles style) {
      int line = position.line;
      if (line < 0 || line >= text.getLineCount()) return false;

      String before = text.subContent(line, 0, line, position.column).toString();

      String after =
          text.subContent(line, position.column, line, text.getLine(line).length()).toString();

      return before.endsWith(start) && after.startsWith(end);
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
      int advanceBefore = getIndentAdvance(before); // or your own helper
      int advanceAfter = getIndentAdvance(after);

      String indentBefore = TextUtils.createIndent(indentBase + advanceBefore, tabSize, false);
      String indentAfter = TextUtils.createIndent(indentBase + advanceAfter, tabSize, false);

      StringBuilder insert =
          new StringBuilder("\n").append(indentBefore).append('\n').append(indentAfter);

      int cursorShiftBack = indentAfter.length() + 1;
      return new NewlineHandleResult(insert, cursorShiftBack);
    }
  }
}
