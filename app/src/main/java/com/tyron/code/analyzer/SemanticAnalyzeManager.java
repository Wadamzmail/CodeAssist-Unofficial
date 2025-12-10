package com.tyron.code.analyzer;

import com.tyron.code.analyzer.semantic.SemanticToken;
import com.tyron.code.language.HighlightUtil;
import com.tyron.editor.CharPosition;
import com.tyron.editor.Content;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import org.eclipse.tm4e.core.internal.theme.FontStyle;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import org.eclipse.tm4e.core.internal.theme.ThemeTrieElementRule;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import org.eclipse.tm4e.core.internal.theme.StyleAttributes;
import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.eclipse.tm4e.core.grammar.IGrammar;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;

public abstract class SemanticAnalyzeManager extends DiagnosticTextmateAnalyzer {

  private List<SemanticToken> mSemanticTokens;

  public SemanticAnalyzeManager(
      Editor editor,
      EmptyTextMateLanguage language,
      IGrammar grammar,
      LanguageConfiguration languageConfiguration,
      ThemeRegistry theme)
      throws Exception {
    super(editor,language,grammar, languageConfiguration, theme);
  }

  public abstract List<SemanticToken> analyzeSpansAsync(CharSequence contents);

  @Override
  public void insert(
      io.github.rosemoe.sora.text.CharPosition start,
      io.github.rosemoe.sora.text.CharPosition end,
      CharSequence insertedText) {
    super.insert(start, end, insertedText);
  }

  @Override
  protected void modifyStyles(Styles styles) {
    super.modifyStyles(styles);

    Content content = mEditor.getContent();

    if (mSemanticTokens != null) {
      for (int i = mSemanticTokens.size() - 1; i >= 0; i--) {
        SemanticToken token = mSemanticTokens.get(i);
        if (token.getOffset() > content.length()) {
          continue;
        }
        CharPosition start = mEditor.getCharPosition(token.getOffset());
        CharPosition end = mEditor.getCharPosition(token.getOffset() + token.getLength());

        Span span = Span.obtain(0, getStyle(token));
        HighlightUtil.replaceSpan(
            styles, span, start.getLine(), start.getColumn(), end.getLine(), end.getColumn());
      }
    }
  }

  private long getStyle(SemanticToken token) {
    StyleAttributes style = getTheme().match(token.getTokenType());
    if (style != null) {
        int foreground = style.foregroundId;
        int fontStyle  = style.fontStyle;

        return TextStyle.makeStyle(
                foreground + 255,
                0,
                (fontStyle & FontStyle.Bold)   != 0,
                (fontStyle & FontStyle.Italic) != 0,
                false);
    }
    return 0L;
}

}
