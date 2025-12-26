package com.tyron.code.language.groovy;

import com.tyron.code.language.Language;
import com.tyron.code.language.LanguageManager;
import com.tyron.editor.Editor;
import java.io.File;

public class Groovy implements Language {
  @Override
  public boolean isApplicable(File ext) {
    return ext.getName().endsWith(".groovy") || ext.getName().endsWith(".gradle");
  }

  @Override
  public io.github.rosemoe.sora.lang.Language get(Editor editor) {
    // return new GroovyLanguage(editor);
    return LanguageManager.createTextMateLanguage("source.groovy", true);
  }
}
