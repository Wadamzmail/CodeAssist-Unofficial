@file:OptIn(org.jetbrains.kotlin.analysis.api.KaExperimentalApi::class)

package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.util.CompletionUtils
import com.tyron.completion.DefaultInsertHandler
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.psi.KtExpression

object FirMemberCompletion {

    fun tryComplete(
        file: KotlinFile,
        line: Int,
        column: Int
    ): List<CompletionItem>? {

        if (!FirCompletionUtil.isAfterDot(file, line, column)) return null

        val prefix = FirCompletionUtil.wordPrefix(file, line, column)
        val offset = file.offsetFor(line, column)

        val expr = file.elementAt(offset) as? KtExpression ?: return null

        return analyze(expr) {

            val type = expr.expressionType ?: return@analyze emptyList()

            type.memberScope.callables
                .mapNotNull { symbol: KaCallableSymbol ->

                    val name =
                        symbol.name?.identifier ?: return@mapNotNull null

                    if (!name.startsWith(prefix)) return@mapNotNull null

                    val tail =
                        symbol.returnType?.render() ?: ""

                    CompletionItem.create(
                        name,
                        tail,
                        name,
                        FirIconMapper.iconFrom(symbol)
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
                .sortedBy { it.label }
        }
    }
}