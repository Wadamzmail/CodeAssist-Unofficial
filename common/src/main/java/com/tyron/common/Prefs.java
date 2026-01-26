package com.tyron.common;

import android.content.SharedPreferences;
import android.content.Context;

public class Prefs {
  private static volatile SharedPreferences prefs;
  private static Context ctx;

  private Prefs() {}

  public static void init(Context context,SharedPreferences preferences) {
    ctx = context;
    prefs = preferences;
  }

  public static SharedPreferences get() {
    if (prefs == null) {
      throw new IllegalStateException("Prefs not initialized");
    }
    return prefs;
  }
  
  public static Context getContext(){
     return ctx;
  }
}
