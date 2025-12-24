package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.util.CompletionUtils
import com.tyron.completion.DefaultInsertHandler
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtExpression

object FirMemberCompletion {

    fun tryComplete(
        file: KotlinFile,
        line: Int,
        column: Int
    ): List<CompletionItem>? {

        // لازم نكون بعد نقطة
        if (!FirCompletionUtil.isAfterDot(file, line, column)) {
            return null
        }

        val prefix =
            FirCompletionUtil.wordPrefix(file, line, column)

        val element =
            file.elementAt(file.offsetFor(line, column))

        val expr = element as? KtExpression ?: return null

        return analyze(expr) {

            val type = expr.getKtType() ?: return emptyList()

            type.getMemberScope()
                .getCallableSymbols()
                .mapNotNull { symbol ->

                    val name = symbol.name.asString()

                    if (!name.startsWith(prefix)) return@mapNotNull null

                    val icon =
                        FirIconMapper.iconFrom(symbol)

                    CompletionItem.create(
                        name,
                        symbol.returnType?.asString() ?: "",
                        name,
                        icon
                    ).apply {
                        cursorOffset = commitText.length
                        setInsertHandler(
                            DefaultInsertHandler(
                                CompletionUtils.JAVA_PREDICATE,
                                this
                            )
                        )
                    }
                }
                .distinctBy { it.label }
                .sortedBy { it.label }
        }
    }
}