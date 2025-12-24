/*@file:OptIn(org.jetbrains.kotlin.analysis.api.KaExperimentalApi::class)

package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.model.DrawableKind
import com.tyron.completion.util.CompletionUtils
import com.tyron.completion.DefaultInsertHandler
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.name.FqName

object FirImportCompletion {

    fun tryComplete(
        file: KotlinFile,
        line: Int,
        column: Int
    ): List<CompletionItem>? {

        val lastLine =
            FirCompletionUtil.lastLineBeforeCursor(file, line, column)

        if (!lastLine.trimStart().startsWith("import")) return null

        val prefix =
            FirCompletionUtil.importPrefix(file, line, column)

        return analyze(file.kotlinFile) {

            val provider = symbolProvider

            val parent =
                if (prefix.contains('.'))
                    FqName(prefix.substringBeforeLast('.'))
                else
                    FqName.ROOT

            provider.getPackageSymbols(parent)
                .mapNotNull { symbol: KaPackageSymbol ->

                    val fqName = symbol.fqName.asString()
                    if (!fqName.startsWith(prefix)) return@mapNotNull null

                    CompletionItem.create(
                        fqName,
                        "import",
                        fqName,
                        DrawableKind.Package
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
}*/