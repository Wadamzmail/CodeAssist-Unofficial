/*package dev.mutwakil.completion.kotlin.fir

import com.tyron.completion.model.CompletionItem
import com.tyron.completion.model.DrawableKind
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import com.tyron.kotlin.completion.KotlinFile 

object FirKeywordCompletion {

    fun tryComplete(
        file: KotlinFile,
        line: Int,
        column: Int
    ): List<CompletionItem>? {

        val prefix =
            FirCompletionUtil.wordPrefix(file, line, column)

        if (prefix.isEmpty()) return null

        return (KtTokens.KEYWORDS.types + KtTokens.SOFT_KEYWORDS.types)
            .mapNotNull { token ->
                if (token is KtKeywordToken && token.value.startsWith(prefix)) {
                    CompletionItem(
                        token.value,
                        "Keyword",
                        token.value,
                        DrawableKind.Keyword
                    )
                } else null
            }
    }
}*/