package com.tyron.code.language.kotlin;

import android.util.Log;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.DiagnosticTextmateAnalyzer;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.ui.editor.IDEEditor;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.editor.Editor;
import com.tyron.kotlin.completion.KotlinEnvironment;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import java.util.ArrayList;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

public class KotlinAnalyzer2 extends DiagnosticTextmateAnalyzer {

  private static final String GRAMMAR_NAME = "kotlin.tmLanguage";
  private static final String LANGUAGE_PATH = "textmate/kotlin/syntaxes/kotlin.tmLanguage";
  private static final String CONFIG_PATH = "textmate/kotlin/language-configuration.json";
  private static final String SCOPENAME = "source.kotlin";
  private final String TAG = "KotlinAnalyzer";
  private ArrayList<DiagnosticWrapper> diagnostics = new ArrayList<>();
  private KotlinEnvironment kotlinEnvironment;
  private Editor editor;

  public static KotlinAnalyzer2 create(Editor editor, EmptyTextMateLanguage lang) {
    try {
      return new KotlinAnalyzer2(
          editor,
          lang,
          GrammarRegistry.getInstance().findGrammar(SCOPENAME),
          GrammarRegistry.getInstance().findLanguageConfiguration(SCOPENAME),
          ThemeRegistry.getInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public KotlinAnalyzer2(
      Editor editor,
      EmptyTextMateLanguage lang,
      IGrammar grammar,
      LanguageConfiguration languageConfiguration,
      ThemeRegistry theme)
      throws Exception {
    super(editor, lang, grammar, languageConfiguration, theme);
    this.editor = editor;
  }

  @Override
  public void analyzeInBackground(CharSequence content) {

    if (mEditor == null) {
      return;
    }

    Project currentProject = ProjectManager.getInstance().getCurrentProject();
    if (currentProject != null) {
      Module module = currentProject.getModule(mEditor.getCurrentFile());
      if (module instanceof AndroidModule) {

        if (ApplicationLoader.getDefaultPreferences()
            .getBoolean(SharedPreferenceKeys.KOTLIN_HIGHLIGHTING, true)) {
          diagnostics.clear();
          ProgressManager.getInstance()
              .runLater(
                  () -> {
                    try {
                      mEditor.setAnalyzing(true);
                      doAnalysis();
                    } catch (Exception e) {
                      Log.e(TAG, "Analysis failed", e);
                    }
                    ProgressManager.getInstance().runLater(() -> mEditor.setAnalyzing(false), 300);
                  },
                  900);
        }
      }
    }
  }

  private void doAnalysis() {
    ((IDEEditor) editor).analyze();
  }
}
