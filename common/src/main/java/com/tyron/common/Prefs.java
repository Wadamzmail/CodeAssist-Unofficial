package com.tyron.common;

import android.content.SharedPreferences;

public class Prefs {
  private static volatile SharedPreferences prefs;

  private Prefs() {}

  public static void init(SharedPreferences preferences) {
    prefs = preferences;
  }

  public static SharedPreferences get() {
    if (prefs == null) {
      throw new IllegalStateException("Prefs not initialized");
    }
    return prefs;
  }
}
