package com.tyron.common.util

//import dev.mutwakil.androidide.preferences.internal.EditorPreferences

/** @author Akash Yadav */

/**
 * The indentation character to use. If [useSoftTab] is enabled, the character is a space, otherwise
 * '\t'.
 */
val indentationChar: Char
  get() = if (/*EditorPreferences.useSoftTab*/true) ' ' else '\t'

/** Get the string which should be used as indentation while generating code. */
val indentationString: String
  get() = if (/*EditorPreferences.useSoftTab*/true) " ".repeat(/*EditorPreferences.tabSize*/4) else "\t"

/**
 * Creates the indentation string for the given number of spaces. The result is simply
 * [indentationChar] repeated [spaceCount] times if [useSoftTab] is enabled, otherwise `spaceCount /
 * tabSize` times.
 *
 * @param spaceCount The number of spaces to indent.
 * @return The indentation string.
 */
fun indentationString(spaceCount: Int): String {
  val count = if (/*EditorPreferences.useSoftTab*/true) spaceCount else spaceCount / /*EditorPreferences.tabSize*/4
  return indentationChar.toString().repeat(count)
}
