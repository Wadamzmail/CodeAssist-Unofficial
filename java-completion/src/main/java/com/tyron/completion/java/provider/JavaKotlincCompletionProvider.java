package com.tyron.completion.java.provider;

import androidx.annotation.NonNull;

import com.tyron.builder.project.api.JavaModule;
import com.tyron.completion.java.util.CompletionItemFactory;
import com.tyron.completion.java.util.JavaCompletionUtil;
import com.tyron.completion.model.CompletionItem;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.psi.completion.JavaKeywordCompletion;
import com.tyron.completion.psi.scope.CompletionElement;
import com.tyron.completion.psi.scope.JavaCompletionProcessor;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.resolve.SymbolCollectingProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.MostlySingularMultiMap;

import java.util.Set;

/**
 * Computes completion candidates using PSI from the kotlin compiler.
 */
public class JavaKotlincCompletionProvider {

    private final JavaModule mJavaModule;

    public JavaKotlincCompletionProvider(JavaModule module) {
        mJavaModule = module;
    }

    public void fillCompletionVariants(@NonNull PsiElement elementAt,
                                       @NonNull CompletionList.Builder builder) {
        PsiElement parent = elementAt.getParent();

        if (elementAt instanceof PsiIdentifier) {
            new JavaKeywordCompletion(elementAt, builder);

            addIdentifierVariants(elementAt, builder);

            addClassNames(elementAt, builder);

            if (parent instanceof PsiJavaCodeReferenceElement) {
                PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement) parent;
                completeReference(elementAt, parentRef, builder);
            }
        }
    }

    private void addClassNames(PsiElement elementAt, CompletionList.Builder builder) {

    }

    private void completeReference(PsiElement position,
                                   PsiJavaCodeReferenceElement parentRef,
                                   CompletionList.Builder builder) {
        PsiElement elementParent = position.getContext();
        if (elementParent instanceof PsiReferenceExpression) {
            final PsiExpression qualifierExpression =
                    ((PsiReferenceExpression) elementParent).getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
                final PsiElement resolve = ((PsiReferenceExpression) qualifierExpression).resolve();
                if (resolve instanceof PsiParameter) {
                    final PsiElement declarationScope = ((PsiParameter)resolve).getDeclarationScope();
                    if (((PsiParameter)resolve).getType() instanceof PsiLambdaParameterType) {
                        final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression) declarationScope;
                        if (PsiTypesUtil.getExpectedTypeByParent(lambdaExpression) == null) {
                            final int parameterIndex = lambdaExpression.getParameterList().getParameterIndex((PsiParameter)resolve);
                            final boolean overloadsFound = LambdaUtil.processParentOverloads(lambdaExpression,  functionalInterfaceType -> {
                                PsiType qualifierType = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, parameterIndex);
                                if (qualifierType instanceof PsiWildcardType) {
                                    qualifierType = ((PsiWildcardType)qualifierType).getBound();
                                }
                                if (qualifierType == null) return;

                                PsiReferenceExpression fakeRef = JavaCompletionUtil
                                        .createReference("xxx.xxx", JavaCompletionUtil
                                                .createContextWithXxxVariable(position, qualifierType));
                                System.out.println(fakeRef);
                            });

                            if (overloadsFound) {
                                // TODO: 3/11/2022  
                            }
                        }
                    }
                }
            }
        }

        JavaCompletionProcessor.Options options = JavaCompletionProcessor.Options.DEFAULT_OPTIONS;
        JavaCompletionProcessor processor = new JavaCompletionProcessor(position, new ElementFilter() {
            @Override
            public boolean isAcceptable(Object o, @Nullable PsiElement psiElement) {
                return true;
            }

            @Override
            public boolean isClassAcceptable(Class aClass) {
                return true;
            }
        }, options, Condition.TRUE);
        parentRef.processVariants(processor);
        Iterable<CompletionElement> results = processor.getResults();
        results.forEach(result -> {
            CompletionItem item = CompletionItemFactory.forPsiElement(
                    (PsiNamedElement) result.getElement());
            builder.addItem(item);
        });
    }

    private void addIdentifierVariants(PsiElement elementAt, CompletionList.Builder builder) {

    }
}
