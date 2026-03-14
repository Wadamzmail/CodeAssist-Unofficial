package com.tyron.common.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources.Theme
import android.util.TypedValue

fun Context.isSystemInDarkMode(): Boolean {
  return this.resources.configuration.isSystemInDarkMode()
}

fun Configuration.isSystemInDarkMode(): Boolean {
  return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@JvmOverloads
fun Context.resolveAttr(id: Int, resolveRefs: Boolean = true): Int {
  return theme.resolveAttr(id, resolveRefs)
}

@JvmOverloads
fun Theme.resolveAttr(id: Int, resolveRefs: Boolean = true): Int =
  TypedValue().let {
    resolveAttribute(id, it, resolveRefs)
    it.data
  }
