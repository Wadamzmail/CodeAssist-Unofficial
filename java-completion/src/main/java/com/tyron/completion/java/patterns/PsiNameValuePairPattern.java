package com.tyron.completion.java.patterns;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiNamePatternCondition;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public final class PsiNameValuePairPattern extends PsiElementPattern<PsiNameValuePair, PsiNameValuePairPattern> {
    static final PsiNameValuePairPattern NAME_VALUE_PAIR_PATTERN = new PsiNameValuePairPattern();

    private PsiNameValuePairPattern() {
        super(PsiNameValuePair.class);
    }

    @NotNull
    public PsiNameValuePairPattern withName(@NotNull @NonNls final String requiredName) {
        return with(new PatternCondition<PsiNameValuePair>("withName") {
            @Override
            public boolean accepts(@NotNull final PsiNameValuePair psiNameValuePair, final ProcessingContext context) {
                String actualName = psiNameValuePair.getName();
                return requiredName.equals(actualName) || actualName == null && "value".equals(requiredName);
            }
        });
    }
}