package com.tyron.common;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
  private static volatile SharedPreferences prefs;
  private static Application application;

  private Prefs() {}

  public static void init(Application app, SharedPreferences preferences) {
    application = app;
    prefs = preferences;
  }

  public static SharedPreferences get() {
    if (prefs == null) {
      throw new IllegalStateException("Prefs not initialized");
    }
    return prefs;
  }

  public static Application getApplication() {
    return application;
  }

  public static Context getContext() {
    return application.getApplicationContext();
  }
}
