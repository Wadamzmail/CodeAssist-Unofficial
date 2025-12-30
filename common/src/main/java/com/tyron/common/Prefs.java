package com.tyron.common;

import android.content.SharedPreferences;

public class Prefs{
public static SharedPreferences prefs;
private Prefs(){}

public static void init(SharedPreferences prefs){
  if(prefs==null)return;
  this.prefs = prefs;
}

}