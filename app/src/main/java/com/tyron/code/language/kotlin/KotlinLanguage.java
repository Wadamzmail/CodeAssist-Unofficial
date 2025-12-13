package com.tyron.code.language.kotlin;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tyron.builder.BuildModule;
import com.tyron.code.language.CompletionItemWrapper;
import com.tyron.code.language.LanguageManager;
import com.tyron.completion.DefaultInsertHandler;
import com.tyron.completion.java.provider.JavaSortCategory;
import com.tyron.completion.model.CompletionItem;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.util.CompletionUtils;
import com.tyron.editor.Editor;
import com.tyron.kotlin.completion.KotlinEnvironment;
import com.tyron.kotlin.completion.KotlinFile;

import java.io.File;
import java.util.List;
import java.util.Objects;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.TextRange;
import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;


public class KotlinLanguage extends EmptyTextMateLanguage implements Language {

    private static final String GRAMMAR_NAME = "kotlin.tmLanguage";
    private static final String LANGUAGE_PATH = "textmate/kotlin/syntaxes/kotlin.tmLanguage";
    private static final String CONFIG_PATH = "textmate/kotlin/language-configuration.json";
    private static final String SCOPE_NAME = "source.kotlin";

    private final TextMateLanguage delegate;
    private final Editor editor;
    public boolean createIdentifiers = false;
    
    private final Formatter formatter = new AsyncFormatter() {
        @Nullable
        @Override
        public TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange) {
             String formatted;
        try {
            formatted = com.facebook.ktfmt.format.Formatter.format(text.toString(),false);
        } catch (Exception e) {
            formatted = text.toString(); 
        }

        if (!text.toString().equals(formatted)) {
            int oldCursor = cursorRange.getStartIndex();
            text.delete(0, text.length());
            text.insert(0, 0, formatted);
            int newCursor = Math.min(oldCursor, formatted.length());
            CharPosition pos = text.getIndexer().getCharPosition(newCursor);
            return new TextRange(pos, pos);
        }

        return cursorRange;
        }

        @Nullable
        @Override
        public TextRange formatRegionAsync(@NonNull Content text,
                                           @NonNull TextRange rangeToFormat,
                                           @NonNull TextRange cursorRange) {
            return null;
        }
    };

    private KotlinEnvironment kotlinEnvironment;

    public KotlinLanguage(Editor editor) {
        this.editor = editor;
        delegate = LanguageManager.createTextMateLanguage(SCOPE_NAME);
    }

    @NonNull
    @Override
    public AnalyzeManager getAnalyzeManager() {
        return delegate.getAnalyzeManager();
    }

    @Override
    public int getInterruptionLevel() {
        return delegate.getInterruptionLevel();
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content,
                                    @NonNull CharPosition position,
                                    @NonNull CompletionPublisher publisher,
                                    @NonNull Bundle extraArguments) throws CompletionCancelledException {
        String identifierPart = CompletionHelper.computePrefix(content, position, CompletionUtils.JAVA_PREDICATE::test);
        KotlinAutoCompleteProvider provider =
                new KotlinAutoCompleteProvider(editor);
        CompletionList completionList = provider.getCompletionList(identifierPart,
                position.getLine(),
                position.getColumn());
        if (completionList == null) {
            return;
        }
        completionList.getItems().stream().map(CompletionItemWrapper::new).forEach(publisher::addItem);
    }

    private KotlinEnvironment getOrCreateKotlinEnvironment() {
        if (kotlinEnvironment == null) {
            kotlinEnvironment =
                    KotlinEnvironment.Companion.with(List.of(Objects.requireNonNull(BuildModule.getAndroidJar()),
                            BuildModule.getLambdaStubs()));
        }
        return kotlinEnvironment;
    }

    @Override
    public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
        return delegate.getIndentAdvance(content, line, column);
    }

    @Override
    public boolean useTab() {
        return delegate.useTab();
    }

    @NonNull
    @Override
    public Formatter getFormatter() {
        return formatter;
    }

    @Override
    public SymbolPairMatch getSymbolPairs() {
        return delegate.getSymbolPairs();
    }

    @Nullable
    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return new NewlineHandler[0];
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }
}
