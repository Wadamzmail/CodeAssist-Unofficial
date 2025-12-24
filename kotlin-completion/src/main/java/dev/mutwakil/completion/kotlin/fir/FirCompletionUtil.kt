package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile

object FirCompletionUtil {

    /**
     * يرجّع النص قبل الكيرسر مباشرة
     */
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

    /**
     * يرجّع آخر سطر قبل الكيرسر
     */
    fun lastLineBeforeCursor(
        file: KotlinFile,
        line: Int,
        column: Int
    ): String {
        val before = textBeforeCursor(file, line, column)
        return before.substringAfterLast('\n')
    }

    /**
     * prefix للكلمات (حروف فقط)
     * مثال: "pri" من "print"
     */
    fun wordPrefix(
        file: KotlinFile,
        line: Int,
        column: Int
    ): String {
        val lineText = lastLineBeforeCursor(file, line, column)
        return lineText.takeLastWhile { it.isLetter() }
    }

    /**
     * prefix للإيمبورت
     * مثال: "android.widget"
     */
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
    
    /**
     * هل الكيرسر بعد نقطة مباشرة
     */
    fun isAfterDot(
       file: KotlinFile,
       line: Int,
       column: Int
    ): Boolean {
      val before = textBeforeCursor(file, line, column)
      return before.endsWith(".")
    }

    /**
     * يرجّع النص قبل آخر نقطة
     * مثال: "list" من "list.ele"
     */
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
    
    
}