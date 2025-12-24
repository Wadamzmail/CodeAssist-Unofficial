package dev.mutwakil.completion.kotlin.engine

import dev.mutwakil.completion.kotlin.env.FirKotlinEnvironment

class FirCompletionEngine(
    private val environment: FirKotlinEnvironment
) {

    data class Result(
        val afterDot: Boolean,
        val prefix: String
    )

    fun analyze(code: String, offset: Int): Result {
        if (offset <= 0 || offset > code.length) {
            return Result(false, "")
        }

        val before = code.substring(0, offset)

        val afterDot = before.endsWith(".")

        val prefix = buildPrefix(before)

        return Result(afterDot, prefix)
    }

    private fun buildPrefix(text: String): String {
        val sb = StringBuilder()
        var i = text.length - 1

        while (i >= 0) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '_') {
                sb.append(c)
            } else {
                break
            }
            i--
        }

        return sb.reverse().toString()
    }
}