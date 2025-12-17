package com.tyron.completion.java.patterns;

import androidx.annotation.NonNull;

import com.tyron.completion.java.patterns.elements.JavacElementPattern;
import com.tyron.completion.java.patterns.elements.JavacElementPatternConditionPlus;

import org.jetbrains.annotations.Nullable;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PatternCondition;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import javax.lang.model.element.Element;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;

public class JavacTreeMemberPattern<T extends Tree, Self extends JavacTreeMemberPattern<T, Self>> extends JavacTreePattern<T, Self> implements JavacElementPattern {

    protected JavacTreeMemberPattern(@NonNull InitialPatternCondition<T> condition) {
        super(condition);
    }

    protected JavacTreeMemberPattern(Class<T> aClass) {
        super(aClass);
    }

    public Self inClass(@NonNull String fqn) {
        return inClass(JavacTreePatterns.classTree().withQualifiedName(fqn));
    }

    public Self inClass(final ElementPattern elementPattern) {
        return with(new JavacElementPatternConditionPlus<T, ClassTree>("inClass", elementPattern) {
            @Override
            public boolean processValues(T t, ProcessingContext processingContext,
                                         PairProcessor<ClassTree, ProcessingContext> pairProcessor) {
                return elementPattern.accepts(t, processingContext);
            }

            @Override
            public boolean processValues(Element target, ProcessingContext context,
                                         PairProcessor<Element, ProcessingContext> processor) {
                return false;
            }
        });
    }


    @Override
    public boolean accepts(@Nullable Object o, ProcessingContext context) {
        if (o instanceof Tree) {
            return super.accepts(o, context);
        }
        if (o instanceof Element) {
            Element element = (Element) o;
            for (PatternCondition<? super T> condition : getCondition().getConditions()) {
                if (condition instanceof JavacElementPattern) {
                    if (!((JavacElementPattern) condition).accepts(element, context)) {
                        return false;
                    }
                }
            }

            return true;
        }
        return false;
    }


    @Override
    public boolean accepts(Element element, ProcessingContext context) {
        return accepts((Object) element, context);
    }
}
