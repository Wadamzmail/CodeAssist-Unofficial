package com.tyron.common;

import android.content.SharedPreferences;

public class Prefs{
public static SharedPreferences prefs;
private Prefs(){}

public static void init(SharedPreferences preferences){
  if(prefs==null)return;
  prefs = preferences;
}

}