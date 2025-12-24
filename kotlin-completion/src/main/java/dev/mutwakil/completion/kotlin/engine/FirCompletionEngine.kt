package dev.mutwakil.completion.kotlin.engine

import dev.mutwakil.completion.kotlin.env.FirKotlinEnvironment
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.types.*
import dev.mutwakil.completion.kotlin.util.FirParser
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.model.DrawableKind

class FirCompletionEngine(
    private val environment: FirKotlinEnvironment
) {

    data class Result(
    val afterDot: Boolean,
    val items: List<CompletionItem>
    )

    fun analyze(code: String, offset: Int): Result {
    if (offset <= 0 || offset > code.length) {
        return Result(false, emptyList())
    }

    val before = code.substring(0, offset)
    val prefix = buildPrefix(before)
    val withoutPrefix = before.dropLast(prefix.length)
    val afterDot = withoutPrefix.endsWith(".")

    if (!afterDot) {
        return Result(false, emptyList())
    }

    val receiver = extractReceiver(withoutPrefix.dropLast(1))
    val items = tryResolveReceiverType(code, receiver, prefix)

    return Result(true, items)
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
    
    
  private fun extractReceiver(text: String): String {
    val sb = StringBuilder()
    var i = text.length - 1

    while (i >= 0) {
        val c = text[i]
        if (
            c.isLetterOrDigit() ||
            c == '_' ||
            c == '.'
        ) {
            sb.append(c)
        } else {
            break
        }
        i--
    }

    return sb.reverse().toString()
   } 
   
  private fun tryResolveReceiverType(
    code: String,
    receiverText: String,
    prefix: String
  ): List<CompletionItem> {
    return try {
        val sessionHolder = environment.sessionHolder

        val wrappedCode = """
            fun __fir_tmp__() {
                $receiverText
            }
        """.trimIndent()

        val firFile = FirParser.parse(wrappedCode, sessionHolder.session)
        sessionHolder.resolveTypes(firFile)

        val function = firFile.declarations
            .filterIsInstance<FirSimpleFunction>()
            .firstOrNull() ?: return emptyList()

        val statement = function.body
            ?.statements
            ?.lastOrNull() as? FirExpression ?: return emptyList()

        val coneType = statement.typeRef.coneType
        val members = collectMembers(coneType)

        toCompletionItems(members, prefix)
     } catch (t: Throwable) {
        emptyList()
     }
   }
   
   private fun collectMembers(
    type: ConeKotlinType
    ): List<String> {
    return try {
        val session = environment.sessionHolder.session
        val scope = type.scope(session)

        val result = mutableListOf<String>()

        scope.getCallableSymbols().forEach { symbol ->
            val name = symbol.callableId?.callableName?.asString()
            if (name != null && !name.startsWith("<")) {
                result.add(name)
            }
        }

        result.distinct().sorted()
    } catch (t: Throwable) {
        emptyList()
    }
  }
   
   private fun ConeKotlinType.renderReadable(): String {
    return this.toString()
        .substringAfterLast('.')
        .substringBefore('<')
    } 
   
   private fun toCompletionItems(
    names: List<String>,
    prefix: String
    ): List<CompletionItem> {
    return names
        .filter { it.startsWith(prefix) }
        .map { name ->
            CompletionItem(
                name,
                "FIR",
                name,
                DrawableKind.Method
            )
        }
   }

}