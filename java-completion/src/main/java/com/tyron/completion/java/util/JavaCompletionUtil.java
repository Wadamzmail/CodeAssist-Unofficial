package com.tyron.completion.java.util;

import androidx.annotation.NonNull;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;

public class JavaCompletionUtil {
    @NonNull
    public static PsiReferenceExpression createReference(@NonNull String text, @NonNull PsiElement context) {
        return (PsiReferenceExpression) JavaPsiFacade
                .getElementFactory(context.getProject()).createExpressionFromText(text, context);
    }

    public static FakePsiElement createContextWithXxxVariable(@NonNull PsiElement place, @NonNull PsiType varType) {
        return new FakePsiElement() {
            @Override
            public boolean processDeclarations(@NonNull PsiScopeProcessor processor,
                                               @NonNull ResolveState state,
                                               PsiElement lastParent,
                                               @NonNull PsiElement place) {
                return processor.execute(new LightVariableBuilder<>("xxx", varType, place), ResolveState.initial());
            }

            @Override
            public PsiElement getParent() {
                return place;
            }
        };
    }
}
