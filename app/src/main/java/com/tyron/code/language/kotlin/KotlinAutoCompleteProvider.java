package com.tyron.code.language.kotlin;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.language.AbstractAutoCompleteProvider;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.model.CompletionList;
import com.tyron.editor.Editor;
import com.tyron.kotlin.completion.KotlinEnvironment;
import com.tyron.kotlin.completion.KotlinFile;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;

public class KotlinAutoCompleteProvider extends AbstractAutoCompleteProvider {

  private static final String TAG = KotlinAutoCompleteProvider.class.getSimpleName();

  private final Editor mEditor;
  private final SharedPreferences mPreferences;

  private KotlinCoreEnvironment environment;

  public KotlinAutoCompleteProvider(Editor editor) {
    mEditor = editor;
    mPreferences = ApplicationLoader.getDefaultPreferences();
  }

  @Nullable
  @Override
  public CompletionList getCompletionList(String prefix, int line, int column) {
    if (!mPreferences.getBoolean(SharedPreferenceKeys.KOTLIN_COMPLETIONS, false)) {
      return null;
    }

    if (com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
      return null;
    }

    if (!mPreferences.getBoolean(SharedPreferenceKeys.KOTLIN_COMPLETIONS, false)) {
      return null;
    }

    Project project = ProjectManager.getInstance().getCurrentProject();
    if (project == null) {
      return null;
    }

    Module currentModule = project.getModule(mEditor.getCurrentFile());

    if (!(currentModule instanceof AndroidModule)) {
      return null;
    }

    KotlinEnvironment kotlinEnvironment = KotlinEnvironment.Companion.get(currentModule);
    if (kotlinEnvironment == null) {
      return null;
    }

    KotlinFile updatedFile =
        kotlinEnvironment.updateKotlinFile(
            mEditor.getCurrentFile().getAbsolutePath(), mEditor.getContent().toString());
    //  List<CompletionItem> itemList =
    return kotlinEnvironment.complete(updatedFile, line, column);

    // return CompletionList.builder(prefix).addItems(itemList).build();
  }

  /*  @Nullable
  public List<CompletionItem> getCompletionItems(String prefix, int line, int column) {
    if (!mPreferences.getBoolean(SharedPreferenceKeys.KOTLIN_COMPLETIONS, false)) {
      return null;
    }

    if (com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
      return null;
    }

    if (!mPreferences.getBoolean(SharedPreferenceKeys.KOTLIN_COMPLETIONS, false)) {
      return null;
    }

    Project project = ProjectManager.getInstance().getCurrentProject();
    if (project == null) {
      return null;
    }

    Module currentModule = project.getModule(mEditor.getCurrentFile());

    if (!(currentModule instanceof AndroidModule)) {
      return null;
    }

    KotlinEnvironment kotlinEnvironment = KotlinEnvironment.Companion.get(currentModule);
    if (kotlinEnvironment == null) {
      return null;
    }

    KotlinFile updatedFile =
        kotlinEnvironment.updateKotlinFile(
            mEditor.getCurrentFile().getAbsolutePath(), mEditor.getContent().toString());

    return kotlinEnvironment.complete(updatedFile, line, column);
  }
  */
  @Override
  public String getPrefix(Editor editor, int line, int column) {
    String empty = "";
    Project project = ProjectManager.getInstance().getCurrentProject();
    if (project == null) {
      return empty;
    }

    Module currentModule = project.getModule(mEditor.getCurrentFile());

    if (!(currentModule instanceof AndroidModule)) {
      return empty;
    }

    KotlinEnvironment kotlinEnvironment = KotlinEnvironment.Companion.get(currentModule);
    if (kotlinEnvironment == null) {
      System.out.println(TAG + ": KotlinEnvironment is null");
      return empty;
    }

    KotlinFile kotlinFile =
        kotlinEnvironment.getKotlinFile(editor.getCurrentFile().getAbsolutePath());
    if (kotlinFile == null) {
      return empty;
    }

    PsiElement psiElement = kotlinFile.elementAt(line, column);
    if (psiElement == null) {
      return empty;
    }
    return kotlinEnvironment.getPrefix(psiElement);
  }
}
