/*package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile

object FirCompletionUtil {
 
    fun textBeforeCursor(
        file: KotlinFile,
        line: Int,
        column: Int
    ): String {
        val offset = file.offsetFor(line, column)
        val text = file.kotlinFile.text
        return if (offset in 0..text.length) {
            text.substring(0, offset)
        } else {
            ""
        }
    }

 
    fun lastLineBeforeCursor(
        file: KotlinFile,
        line: Int,
        column: Int
    ): String {
        val before = textBeforeCursor(file, line, column)
        return before.substringAfterLast('\n')
    }

 
    fun wordPrefix(
        file: KotlinFile,
        line: Int,
        column: Int
    ): String {
        val lineText = lastLineBeforeCursor(file, line, column)
        return lineText.takeLastWhile { it.isLetter() }
    }

 
    fun importPrefix(
        file: KotlinFile,
        line: Int,
        column: Int
    ): String {
        val lineText = lastLineBeforeCursor(file, line, column)
        return lineText
            .removePrefix("import")
            .trim()
    }
    
 
    fun isAfterDot(
       file: KotlinFile,
       line: Int,
       column: Int
    ): Boolean {
      val before = textBeforeCursor(file, line, column)
      return before.endsWith(".")
    }

 
    fun receiverText(
      file: KotlinFile,
      line: Int,
      column: Int
    ): String {
    val before = textBeforeCursor(file, line, column)
    return before
        .substringBeforeLast('.', "")
        .takeLastWhile {
            it.isLetterOrDigit() || it == '_'
        }
    }
    
    
}*/