package com.tyron.code.language.textmate;

import android.os.Bundle;
import androidx.annotation.NonNull;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

public class EmptyTextMateLanguage extends EmptyLanguage implements Language {
  public boolean createIdentifiers = false;
  protected int tabSize = 4;

  @Override
  public void destroy() {
    super.destroy();
  }

  @Override
  public io.github.rosemoe.sora.lang.analysis.AnalyzeManager getAnalyzeManager() {
    return super.getAnalyzeManager();
  }

  @Override
  public io.github.rosemoe.sora.lang.format.Formatter getFormatter() {
    return super.getFormatter();
  }

  @Override
  public int getIndentAdvance(
      @NonNull ContentReference content,
      int line,
      int column,
      int spaceCountOnLine,
      int tabCountOnLine) {
    return super.getIndentAdvance(content, line, column, spaceCountOnLine, tabCountOnLine);
  }

  @Override
  public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
    return super.getIndentAdvance(content, line, column);
  }

  @Override
  public int getInterruptionLevel() {
    return super.getInterruptionLevel();
  }

  @Override
  public io.github.rosemoe.sora.lang.smartEnter.NewlineHandler[] getNewlineHandlers() {
    return super.getNewlineHandlers();
  }

  @Override
  public io.github.rosemoe.sora.lang.QuickQuoteHandler getQuickQuoteHandler() {
    return super.getQuickQuoteHandler();
  }

  @Override
  public io.github.rosemoe.sora.widget.SymbolPairMatch getSymbolPairs() {
    return super.getSymbolPairs();
  }

  @Override
  public void requireAutoComplete(
      @NonNull ContentReference content,
      @NonNull CharPosition position,
      @NonNull CompletionPublisher publisher,
      @NonNull Bundle extraArguments) {
    super.requireAutoComplete(content, position, publisher, extraArguments);
  }

  public void setTabSize(int tabSize) {
        this.tabSize = tabSize;
    }

    public int getTabSize() {
        return tabSize;
    }

  @Override
  public boolean useTab() {
    return super.useTab();
  }
}
