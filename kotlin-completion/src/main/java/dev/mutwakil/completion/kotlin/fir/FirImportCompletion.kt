package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.model.DrawableKind
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtFile
import com.tyron.completion.util.CompletionUtils
import com.tyron.completion.DefaultInsertHandler

object FirImportCompletion {

    fun tryComplete(
        file: KotlinFile,
        line: Int,
        column: Int
    ): List<CompletionItem>? {

        val lastLine =
            FirCompletionUtil.lastLineBeforeCursor(file, line, column)

        if (!lastLine.trimStart().startsWith("import")) {
            return null
        }

        val prefix =
            FirCompletionUtil.importPrefix(file, line, column)

        return analyze(file.kotlinFile) {
            getTopLevelPackagesAndClasses()
                .mapNotNull { symbol ->
                    val fqName = symbol.fqName?.asString() ?: return@mapNotNull null
                    if (fqName.startsWith(prefix)) {
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
                    } else null
                }
                .distinctBy { it.label }
                .sortedBy { it.label }
        }
    }
}