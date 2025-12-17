package com.tyron.completion.psi.completion;

import static com.tyron.completion.java.patterns.PsiElementPatterns.insideStarting;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.string;
import static com.intellij.patterns.StandardPatterns.or;

import com.tyron.completion.java.patterns.PsiAnnotationPattern;
import com.tyron.completion.java.patterns.PsiElementPatterns;
import com.tyron.completion.java.util.CompletionItemFactory;
import com.tyron.completion.model.CompletionList;
import com.tyron.language.java.JavaFileType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeCodeFragment;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class JavaKeywordCompletion {

    public static final ElementPattern<PsiElement> AFTER_DOT =
            psiElement().afterLeaf(psiElement().withText(StandardPatterns.string().oneOf(".")));

    static final ElementPattern<PsiElement> VARIABLE_AFTER_FINAL = psiElement()
            .afterLeaf(psiElement().withText(StandardPatterns.string().oneOf(PsiKeyword.FINAL)))
            .inside(psiElement(PsiDeclarationStatement.class));

    private static boolean isStatementCodeFragment(PsiFile file) {
        return file instanceof JavaCodeFragment &&
               !(file instanceof PsiExpressionCodeFragment ||
                 file instanceof PsiJavaCodeReferenceCodeFragment ||
                 file instanceof PsiTypeCodeFragment);
    }

    static boolean isEndOfBlock(@NotNull PsiElement element) {
        PsiElement prev = prevSignificantLeaf(element);
        if (prev == null) {
            PsiFile file = element.getContainingFile();
            return !(file instanceof PsiCodeFragment) || isStatementCodeFragment(file);
        }

        if (psiElement().inside(PsiAnnotationPattern.PSI_ANNOTATION_PATTERN).accepts(prev)) {
            return false;
        }

        if (prev instanceof OuterLanguageElement) {
            return true;
        }
        if (psiElement().withText(string().oneOf("{", "}", ";", ":", "else")).accepts(prev)) {
            return true;
        }
        if (prev.textMatches(")")) {
            PsiElement parent = prev.getParent();
            if (parent instanceof PsiParameterList) {
                return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevVisibleLeaf(element),
                                                   PsiDocComment.class) != null;
            }

            return !(parent instanceof PsiExpressionList ||
                     parent instanceof PsiTypeCastExpression ||
                     parent instanceof PsiRecordHeader);
        }

        return false;
    }

    static final Set<String> PRIMITIVE_TYPES = ContainerUtil.newLinkedHashSet(
            Arrays.asList(PsiKeyword.SHORT, PsiKeyword.BOOLEAN, PsiKeyword.DOUBLE, PsiKeyword.LONG,
                          PsiKeyword.INT, PsiKeyword.FLOAT, PsiKeyword.CHAR, PsiKeyword.BYTE));

    private final PsiElement myPosition;
    private final CompletionList.Builder myBuilder;

    public JavaKeywordCompletion(PsiElement position, CompletionList.Builder builder) {
        myPosition = position;
        myBuilder = builder;

            addKeywords();
    }

    private static PsiElement prevSignificantLeaf(PsiElement position) {
       return position;
    }

    public void addKeywords() {
        if (PsiTreeUtil.getNonStrictParentOfType(myPosition, PsiLiteralExpression.class, PsiComment.class) != null) {
            return;
        }

        if (psiElement().afterLeaf(psiElement().withText(StandardPatterns.string().oneOf("::"))).accepts(myPosition)) {
            return;
        }

        PsiFile file = myPosition.getContainingFile();
        if (PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) && PsiUtil
                .isLanguageLevel9OrHigher(file)) {
//            addModuleKeywords();
            return;
        }

        myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.FINAL));

        boolean statementPosition = isStatementPosition(myPosition);
        if (statementPosition) {

        }

        addExpressionKeywords(statementPosition);

//        addThisSuper();

        addInstanceOf();

        addClassKeywords();
    }

    private void addClassKeywords() {
        if (isSuitableForClass(myPosition)) {
            for (String modifier : PsiModifier.MODIFIERS) {
                myBuilder.addItem(CompletionItemFactory.keyword(modifier));
            }

            if (insideStarting(or(psiElement(PsiLocalVariable.class), psiElement(PsiExpression.class))).accepts(myPosition)) {
                myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.CLASS));
                myBuilder.addItem(CompletionItemFactory.item("abstract class"));
            }

            if (PsiTreeUtil.getParentOfType(myPosition, PsiExpression.class, true, PsiMember.class) == null &&
                PsiTreeUtil.getParentOfType(myPosition, PsiCodeBlock.class, true, PsiMember.class) == null) {
                List<String> keywords = new ArrayList<>();
                keywords.add(PsiKeyword.CLASS);
                keywords.add(PsiKeyword.INTERFACE);

                if (PsiUtil.isLanguageLevel5OrHigher(myPosition)) {
                    keywords.add(PsiKeyword.ENUM);
                }

                // TODO: recommend class declaration
                for (String keyword : keywords) {
                    myBuilder.addItem(CompletionItemFactory.keyword(keyword));
                }
            }
        }
    }

    private void addPrimitiveTypes() {

    }

    private void addExpressionKeywords(boolean statementPosition) {
        if (isExpressionPosition(myPosition)) {
            PsiElement parent = myPosition.getParent();
            PsiElement grandParent = parent == null ? null : parent.getParent();
            boolean allowExprKeywords = !(grandParent instanceof PsiExpressionStatement) && !(grandParent instanceof PsiUnaryExpression);
            if (PsiTreeUtil.getParentOfType(myPosition, PsiAnnotation.class) == null) {
                if (!statementPosition) {
                    myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.NEW));
                }
                if (allowExprKeywords) {
                    myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.NULL));
                }
            }

            if (allowExprKeywords && mayExpectBoolean()) {
                myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.TRUE));
                myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.FALSE));
            }
        }

        if (isQualifiedNewContext()) {
            myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.NEW));
        }
    }

    private boolean isQualifiedNewContext() {
        if (myPosition.getParent() instanceof PsiReferenceExpression) {
            PsiExpression qualifier = ((PsiReferenceExpression)myPosition.getParent()).getQualifierExpression();
            PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifier == null ? null : qualifier.getType());
            return qualifierClass != null &&
                   ContainerUtil.exists(qualifierClass.getAllInnerClasses(), inner -> canBeCreatedInQualifiedNew(qualifierClass, inner));
        }
        return false;
    }

    private boolean canBeCreatedInQualifiedNew(PsiClass outer, PsiClass inner) {
        PsiMethod[] constructors = inner.getConstructors();
        return !inner.hasModifierProperty(PsiModifier.STATIC) &&
               PsiUtil.isAccessible(inner, myPosition, outer) &&
               (constructors.length == 0 || ContainerUtil.exists(constructors, c -> PsiUtil.isAccessible(c, myPosition, outer)));
    }

    private static boolean mayExpectBoolean() {
        return false;
    }

    private static boolean isExpressionPosition(PsiElement position) {
        if (insideStarting(psiElement(
                PsiClassObjectAccessExpression.class)).accepts(position)) return true;

        PsiElement parent = position.getParent();
        if (!(parent instanceof PsiReferenceExpression) ||
            ((PsiReferenceExpression)parent).isQualified()
//            JavaCompletionContributor.IN_SWITCH_LABEL.accepts(position)) {
        ){
            return false;
        }
        if (parent.getParent() instanceof PsiExpressionStatement) {
            PsiElement previous = PsiTreeUtil.skipWhitespacesBackward(parent.getParent());
            return previous == null || !(previous.getLastChild() instanceof PsiErrorElement);
        }
        return true;
    }

    public static boolean isInstanceofPlace(PsiElement position) {
        PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
        if (prev == null) return false;

        PsiElement expr = PsiTreeUtil.getParentOfType(prev, PsiExpression.class);
        if (expr != null && expr.getTextRange().getEndOffset() == prev.getTextRange().getEndOffset() &&
            PsiTreeUtil.getParentOfType(expr, PsiAnnotation.class) == null) {
            return true;
        }

        if (position instanceof PsiIdentifier && position.getParent() instanceof PsiLocalVariable) {
            PsiType type = ((PsiLocalVariable) position.getParent()).getType();
            if (type instanceof PsiClassType && ((PsiClassType) type).resolve() == null) {
                PsiElement grandParent = position.getParent().getParent();
                return !(grandParent instanceof PsiDeclarationStatement) || !(grandParent.getParent() instanceof PsiForStatement) ||
                       ((PsiForStatement)grandParent.getParent()).getInitialization() != grandParent;
            }
        }

        return false;
    }

    private void addInstanceOf() {
        if (isInstanceofPlace(myPosition)) {
            myBuilder.addItem(CompletionItemFactory.keyword(PsiKeyword.INSTANCEOF));
        }
    }

    public static boolean isSuitableForClass(PsiElement position) {
        if (psiElement().afterLeaf(psiElement().withText("@")).accepts(position) ||
            PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class, PsiExpressionCodeFragment.class) != null) {
            return false;
        }

        PsiElement prev = prevSignificantLeaf(position);
        if (prev == null) {
            return true;
        }

        if (psiElement().without(new PatternCondition<PsiElement>("withoutText") {
            @Override
            public boolean accepts(@NotNull PsiElement psiElement,
                                   ProcessingContext processingContext) {
                return psiElement.getText().equals(".");
            }
        }).inside(
                psiElement(PsiModifierList.class).withParent(
                        PsiJavaPatterns.not(psiElement(PsiParameter.class)).andNot(psiElement(PsiParameterList.class)))).accepts(prev) &&
            (!psiElement().inside(psiElement(PsiAnnotationParameterList.class)).accepts(prev) || prev.textMatches(")"))) {
            return true;
        }

        if (PsiElementPatterns.withParents(PsiErrorElement.class, PsiFile.class).accepts(position)) {
            return true;
        }

        return isEndOfBlock(position);
    }

    private static boolean isForLoopMachinery(PsiElement position) {
        PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class);
        if (statement == null) return false;

        return statement instanceof PsiForStatement ||
               statement.getParent() instanceof PsiForStatement && statement != ((PsiForStatement)statement.getParent()).getBody();
    }

    private static boolean isStatementPosition(PsiElement position) {
        if (psiElement()
                .withSuperParent(2, psiElement(PsiConditionalExpression.class))
                .andNot(insideStarting(psiElement(PsiConditionalExpression.class)))
                .accepts(position)) {
            return false;
        }

        if (isEndOfBlock(position) &&
            PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
            return !isForLoopMachinery(position);
        }

        if (PsiElementPatterns.withParents(PsiReferenceExpression.class, PsiExpressionStatement.class,
                                           PsiIfStatement.class).andNot(
                psiElement().afterLeaf(psiElement().withText("."))).accepts(position)) {
            PsiElement stmt = position.getParent().getParent();
            PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
            return ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt;
        }

        return false;
    }
}