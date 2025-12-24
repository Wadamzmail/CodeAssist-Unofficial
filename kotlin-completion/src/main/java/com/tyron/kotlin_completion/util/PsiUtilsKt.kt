package com.tyron.kotlin_completion.util

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

object PsiUtilsKt {

    @JvmStatic
    fun findParent(element: PsiElement): KtSimpleNameExpression? {
        val parentWithSelf = element.parentsWithSelf
        val sequence = parentWithSelf.filterIsInstance(KtSimpleNameExpression::class.java)
        return sequence.firstOrNull()
    }
}
