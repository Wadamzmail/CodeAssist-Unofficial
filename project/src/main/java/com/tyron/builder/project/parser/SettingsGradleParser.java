package com.tyron.builder.project.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses settings.gradle / settings.gradle.kts and extracts included module names. */
public final class SettingsGradleParser {

  // include ':app', ':lib'
  // include(":app", ":lib")
  private static final Pattern INCLUDE_PATTERN =
      Pattern.compile("include\\s*(?:\\(|)\\s*([^)\\n]+)", Pattern.MULTILINE);

  private static final Pattern MODULE_PATTERN = Pattern.compile("['\"](:[^'\"]+)['\"]");

  private SettingsGradleParser() {}

  public static List<String> parseModules(File projectRoot) throws IOException {
    File settings = findSettingsFile(projectRoot);
    if (settings == null || !settings.exists()) {
      return List.of();
    }

    String content = readAll(settings);
    List<String> modules = new ArrayList<>();

    Matcher includeMatcher = INCLUDE_PATTERN.matcher(content);
    while (includeMatcher.find()) {
      String group = includeMatcher.group(1);

      Matcher moduleMatcher = MODULE_PATTERN.matcher(group);
      while (moduleMatcher.find()) {
        String path = moduleMatcher.group(1);
        modules.add(normalizeModuleName(path));
      }
    }

    return modules;
  }

  private static String normalizeModuleName(String path) {
    // :lib:ui -> lib/ui
    if (path.startsWith(":")) {
      path = path.substring(1);
    }
    return path.replace(':', File.separatorChar);
  }

  private static File findSettingsFile(File root) {
    File groovy = new File(root, "settings.gradle");
    if (groovy.exists()) return groovy;

    File kts = new File(root, "settings.gradle.kts");
    if (kts.exists()) return kts;

    return null;
  }

  private static String readAll(File file) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString();
  }
}
