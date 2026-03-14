package com.tyron.kotlin.completion.codeInsight

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.addToStdlib.sequenceOfLazyValues

object DescriptorToSourceUtilsIde {

    // Returns all PSI elements for descriptor. It can find declarations in builtins or decompiled code.
    fun getAllDeclarations(
        targetDescriptor: DeclarationDescriptor
    ): Collection<PsiElement> {
        val result = getDeclarationsStream(targetDescriptor).toHashSet()
        // filter out elements which are navigate to some other element of the result
        // this is needed to avoid duplicated results for references to declaration in same library source file
        return result.filterNot { element -> element.navigationElement == element }.toList()
    }

    private fun getDeclarationsStream(
        targetDescriptor: DeclarationDescriptor
    ): Sequence<PsiElement> {
        val effectiveReferencedDescriptors =
            DescriptorToSourceUtils.getEffectiveReferencedDescriptors(targetDescriptor).asSequence()
        return effectiveReferencedDescriptors.flatMap { effectiveReferenced ->
            // References in library sources should be resolved to corresponding decompiled declarations,
            // therefore we put both source declaration and decompiled declaration to stream, and afterwards we filter it in getAllDeclarations
            sequenceOfLazyValues(
                { DescriptorToSourceUtils.getSourceFromDescriptor(effectiveReferenced) }
            )
        }.filterNotNull()
    }
}
