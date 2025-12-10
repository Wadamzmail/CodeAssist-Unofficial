package com.tyron.code.language.groovy;

import android.os.Bundle;
import androidx.annotation.NonNull;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.text.Content;
import androidx.annotation.Nullable;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;

public class GroovyLanguage extends EmptyTextMateLanguage implements Language {

  private final Editor mEditor;
  private final GroovyAnalyzer mAnalyzer;
  private final Formatter formatter = new AsyncFormatter() {
        @Nullable
        @Override
        public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
            String format = text.toString();
            if (!text.toString().equals(format)) {
                text.delete(0, text.getLineCount() - 1);
                text.insert(0, 0, format);
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

  public GroovyLanguage(Editor editor) {
    mEditor = editor;
    mAnalyzer = GroovyAnalyzer.create(editor, this);
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

  @Override
  public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
    return 0;
  }

  @Override
  public boolean useTab() {
    return true;
  }

  
  public CharSequence format(CharSequence text) {
    return text;
  }

  @Override
  public SymbolPairMatch getSymbolPairs() {
    return null;
  }

  @Override
  public NewlineHandler[] getNewlineHandlers() {
    return new NewlineHandler[0];
  }

  @Override
  public void destroy() {
    mAnalyzer.destroy();
  }
} 
