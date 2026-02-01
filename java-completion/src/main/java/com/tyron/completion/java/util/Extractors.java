package com.tyron.completion.java.util;

import androidx.annotation.NonNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Extractors {

  private static final Pattern PACKAGE_EXTRACTOR =
      Pattern.compile("^([a-z][_a-zA-Z0-9]*\\.)*[a-z][_a-zA-Z0-9]*");
  private static final Pattern SIMPLE_EXTRACTOR = Pattern.compile("[A-Z][_a-zA-Z0-9]*$");

  @NonNull
  public static String packageName(String className) {
    Matcher matcher = PACKAGE_EXTRACTOR.matcher(className);
    if (matcher.find()) {
      return matcher.group();
    }
    return "";
  }

  @NonNull
  public static String simpleName(String className) {
    Matcher matcher = SIMPLE_EXTRACTOR.matcher(className);
    if (matcher.find()) {
      return matcher.group();
    }
    return "";
  }
}
