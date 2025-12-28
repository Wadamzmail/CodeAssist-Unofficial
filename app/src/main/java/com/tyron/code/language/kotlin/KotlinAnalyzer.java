package com.tyron.code.language.kotlin;

import android.util.Log;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.DiagnosticTextmateAnalyzer;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.editor.Editor;
import com.tyron.kotlin.completion.KotlinEnvironment;
import dev.mutwakil.completion.kotlin.util.KotlinSeverityMapper;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import java.util.ArrayList;
import java.util.Objects;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

public class KotlinAnalyzer extends DiagnosticTextmateAnalyzer {

  private static final String GRAMMAR_NAME = "kotlin.tmLanguage";
  private static final String LANGUAGE_PATH = "textmate/kotlin/syntaxes/kotlin.tmLanguage";
  private static final String CONFIG_PATH = "textmate/kotlin/language-configuration.json";
  private static final String SCOPENAME = "source.kotlin";
  private final String TAG = "KotlinAnalyzer";
  private ArrayList<DiagnosticWrapper> diagnostics = new ArrayList<>();
  private KotlinEnvironment kotlinEnvironment;
  private Editor editor;

  public static KotlinAnalyzer create(Editor editor, EmptyTextMateLanguage lang) {
    try {
      return new KotlinAnalyzer(
          editor,
          lang,
          GrammarRegistry.getInstance().findGrammar(SCOPENAME),
          GrammarRegistry.getInstance().findLanguageConfiguration(SCOPENAME),
          ThemeRegistry.getInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public KotlinAnalyzer(
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
          ProgressManager.getInstance()
              .runLater(
                  () -> {
                    try {
                      mEditor.setAnalyzing(true);
                      diagnostics.clear();

                      Objects.requireNonNull(getKotlinEnvironment())
                          .addIssueListener(
                              issue -> {
                                DiagnosticWrapper wrapper = new DiagnosticWrapper();
                                wrapper.setStartPosition(issue.getStartOffset());
                                wrapper.setEndPosition(issue.getEndOffset());
                                wrapper.setMessage(issue.getMessage());
                                wrapper.setKind(KotlinSeverityMapper.toKind(issue.getSeverity()));
                                if (wrapper.getKind() == null) return kotlin.Unit.INSTANCE;
                                diagnostics.add(wrapper);

                                Objects.requireNonNull((CodeEditorView) mEditor)
                                    .post(
                                        () -> {
                                          editor.setDiagnostics(diagnostics);
                                        });
                                return kotlin.Unit.INSTANCE;
                              });
                              
                      var fileEntry = Objects.requireNonNull(getKotlinEnvironment()).kotlinFiles.get(editor.getCurrentFile().getAbsolutePath());
                      if (fileEntry == null) return;
                      var ktFile = fileEntry.getKotlinFile();      
                              
                      Objects.requireNonNull(getKotlinEnvironment())
                          .analysisOf(
                              Objects.requireNonNull(getKotlinEnvironment())
                                  .kotlinFiles
                                  .values()
                                  .stream()
                                  .map(it -> it.getKotlinFile())
                                  .toList(),
                              ktFile);
                      Objects.requireNonNull(getKotlinEnvironment()).analysis = null;
                      ProgressManager.getInstance()
                          .runLater(() -> mEditor.setAnalyzing(false), 300);

                    } catch (Exception e) {
                      Objects.requireNonNull(getKotlinEnvironment()).analysis = null;
                      ProgressManager.getInstance()
                          .runLater(() -> mEditor.setAnalyzing(false), 300);
                      Log.e(TAG, "Analysis failed", e);    
                    }
                  },
                  900);
        }
      }
    }
  }

  private KotlinEnvironment getKotlinEnvironment() {
    if (kotlinEnvironment == null) {
      try {
        Module currentModule =
            ProjectManager.getInstance()
                .getCurrentProject()
                .getModule(editor.getCurrentFile());
        kotlinEnvironment = KotlinEnvironment.Companion.get(currentModule);
      } catch (Exception e) {
        Log.e(TAG, "failed to initialize KotlinEnvironment", e);
      }
    }
    return kotlinEnvironment;
  }
}
