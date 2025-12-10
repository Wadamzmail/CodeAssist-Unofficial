package com.tyron.code.language.groovy;

import android.content.res.AssetManager;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.eclipse.tm4e.core.grammar.IGrammar;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;

public class GroovyAnalyzer extends BaseTextmateAnalyzer {

  private static final String GRAMMAR_NAME = "groovy.tmLanguage";
  private static final String LANGUAGE_PATH = "textmate/groovy/syntaxes/groovy.tmLanguage";
  private static final String CONFIG_PATH = "textmate/groovy/language-configuration.json";
  private static final String SCOPENAME="source.groovy";

  public GroovyAnalyzer(
      Editor editor,
      EmptyTextMateLanguage lang,
      IGrammar grammar,
      LanguageConfiguration languageConfiguration,
      ThemeRegistry theme)
      throws Exception {
    super(lang,grammar, languageConfiguration, theme);
  }

  public static GroovyAnalyzer create(Editor editor,EmptyTextMateLanguage lang) {
    try {
        return new GroovyAnalyzer(
            editor,
            lang,
             GrammarRegistry.getInstance().findGrammar(SCOPENAME),
            GrammarRegistry.getInstance().findLanguageConfiguration(SCOPENAME),ThemeRegistry.getInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
