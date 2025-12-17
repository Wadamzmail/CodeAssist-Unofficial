package com.tyron.completion.psi.util;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;

public final class CompletionUtilCoreImpl {
    @Nullable
    public static <T extends PsiElement> T getOriginalElement(@NotNull T psi) {
        return getOriginalElement(psi, psi.getContainingFile());
    }

    @Nullable
    public static <T extends PsiElement> T getOriginalElement(@NotNull T psi, PsiFile containingFile) {
        if (containingFile == null || psi instanceof LightElement) return psi;

        PsiFile originalFile = containingFile.getOriginalFile();
        if (psi == containingFile && psi.getClass().isInstance(originalFile)) {
            //noinspection unchecked
            return (T)originalFile;
        }
        TextRange range;
        if (originalFile != containingFile && !(originalFile instanceof PsiCompiledFile) && (range = psi.getTextRange()) != null) {
            Integer start = range.getStartOffset();
            Integer end = range.getEndOffset();

            Document document = containingFile.getViewProvider().getDocument();
            if (document != null) {
                Document hostDocument = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
                OffsetTranslator translator = hostDocument.getUserData(OffsetTranslator.RANGE_TRANSLATION);
                if (translator != null) {
                    if (document instanceof DocumentWindow) {
                        TextRange translated = ((DocumentWindow)document).injectedToHost(new TextRange(start, end));
                        start = translated.getStartOffset();
                        end = translated.getEndOffset();
                    }

                    start = translator.translateOffset(start);
                    end = translator.translateOffset(end);
                    if (start == null || end == null) {
                        return null;
                    }

                    if (document instanceof DocumentWindow) {
                        start = ((DocumentWindow)document).hostToInjected(start);
                        end = ((DocumentWindow)document).hostToInjected(end);
                    }
                }
            }

            //noinspection unchecked
            return (T) PsiTreeUtil.findElementOfClassAtRange(originalFile, start, end, psi.getClass());
        }

        return psi;
    }

    @NotNull
    public static <T extends PsiElement> T getOriginalOrSelf(@NotNull T psi) {
        final T element = getOriginalElement(psi);
        return element == null ? psi : element;
    }
}
