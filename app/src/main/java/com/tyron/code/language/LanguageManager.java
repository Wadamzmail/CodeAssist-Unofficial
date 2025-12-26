package com.tyron.code.language;

import com.tyron.code.language.groovy.Groovy;
import com.tyron.code.language.java.Java;
import com.tyron.code.language.json.Json;
import com.tyron.code.language.kotlin.Kotlin;
import com.tyron.code.language.xml.Xml;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;

public class LanguageManager {

  private static LanguageManager Instance = null;

  public static LanguageManager getInstance() {
    if (Instance == null) {
      Instance = new LanguageManager();
    }
    return Instance;
  }

  private final Set<Language> mLanguages = new HashSet<>();

  private LanguageManager() {
    initLanguages();
  }

  private void initLanguages() {
    mLanguages.addAll(Arrays.asList(new Xml(), new Java(), new Kotlin(), new Groovy(), new Json()));
  }

  public boolean supports(File file) {
    for (Language language : mLanguages) {
      if (language.isApplicable(file)) {
        return true;
      }
    }
    return false;
  }

  public io.github.rosemoe.sora.lang.Language get(Editor editor, File file) {
    for (Language lang : mLanguages) {
      if (lang.isApplicable(file)) {
        return lang.get(editor);
      }
    }
    return null;
  }

  public io.github.rosemoe.sora.lang.Language get(Editor editor, FileObject file) {
    for (Language lang : mLanguages) {
      if (lang.isApplicable(file)) {
        return lang.get(editor);
      }
    }
    return null;
  }

  public static TextMateLanguage createTextMateLanguage(
      String scopeName, boolean createIdentifiers) {

    try {
      /* 1. obtain the registries */
      GrammarRegistry grammarReg = GrammarRegistry.getInstance();

      TextMateLanguage lang =
          TextMateLanguage.create(
              scopeName,
              grammarReg,
              createIdentifiers); // or false if you don’t want built-in completion

      return lang;
    } catch (Exception e) {
      return TextMateLanguage.create(scopeName, false);
    }
  }

  public static TextMateLanguage createTextMateLanguage(String scopeName) {
    return createTextMateLanguage(scopeName, false);
  }
}
