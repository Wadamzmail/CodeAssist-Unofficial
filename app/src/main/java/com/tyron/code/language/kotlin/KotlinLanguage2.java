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
import java.util.ArrayList;

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
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.Project;

//for analysis 
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProcessCanceledException;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import com.tyron.code.ApplicationLoader;
import com.tyron.common.SharedPreferenceKeys;
//test
import android.widget.Toast;
import com.tyron.code.MainActivity;
import android.util.Log;
import com.tyron.completion.model.CompletionList;
import com.tyron.code.language.CachedAutoCompleteProvider;
import com.tyron.builder.model.DiagnosticWrapper;
import dev.mutwakil.completion.kotlin.util.KotlinSeverityMapper;
import com.tyron.completion.lsp.api.ILanguageServer;
import com.tyron.completion.CompletionParameters;
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServer;
import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.util.MyCharacter;
import com.tyron.completion.model.signatures.SignatureHelp;
import com.tyron.completion.model.signatures.SignatureHelpLanguage;
import com.tyron.completion.model.signatures.SignatureHelpLanguageKt;
import com.tyron.completion.model.signatures.SignatureHelpParams;
import com.tyron.completion.lsp.util.DiagnosticUtil;


public class KotlinLanguage2 extends EmptyTextMateLanguage implements Language, SignatureHelpLanguage{

    private static final String GRAMMAR_NAME = "kotlin.tmLanguage";
    private static final String LANGUAGE_PATH = "textmate/kotlin/syntaxes/kotlin.tmLanguage";
    private static final String CONFIG_PATH = "textmate/kotlin/language-configuration.json";
    private static final String SCOPE_NAME = "source.kotlin";
    public static final String TAG = "KotlinLanguage2";

    private final TextMateLanguage delegate;
    private final KotlinAnalyzer2 analyzer;
    private final Editor editor;
    public boolean createIdentifiers = false;
    private final DiagnosticsContainer container = new DiagnosticsContainer();
    private final List<DiagnosticWrapper> diagnostics = new ArrayList<>();
    private Thread analysisThread;
    private volatile boolean analysisRunning = true;
    private final CachedAutoCompleteProvider autoCompleteProvider;
    public KotlinLanguageServer server;
     
    
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
    
   @Override
  public SignatureHelp signatureHelp(SignatureHelpParams params) {
    var signatureHelp = SignatureHelpLanguageKt.unsupportedSignatureHelp();
    if (!com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
      signatureHelp =
          server.signatureHelp(params);
    }
    return signatureHelp;
  }


    public KotlinLanguage2(Editor editor) {
        this.editor = editor;
        delegate = LanguageManager.createTextMateLanguage(SCOPE_NAME);
        analyzer = KotlinAnalyzer2.create(editor,this);
        autoCompleteProvider = new CachedAutoCompleteProvider(editor,
                new KotlinAutoCompleteProvider(editor)); 
    }
    
    private boolean isHighlightEnabled(){
      return ApplicationLoader.getDefaultPreferences().getBoolean(SharedPreferenceKeys.KOTLIN_HIGHLIGHTING, false);
    }

    @NonNull
    @Override
    public AnalyzeManager getAnalyzeManager() {
        return analyzer;
    }

    @Override
    public int getInterruptionLevel() {
        return delegate.getInterruptionLevel();
    }
    
    public boolean isAutoCompleteChar(char p1) {
    return p1 == '.' || MyCharacter.isJavaIdentifierPart(p1);
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content,
                                    @NonNull CharPosition position,
                                    @NonNull CompletionPublisher publisher,
                                    @NonNull Bundle extraArguments) throws CompletionCancelledException {
       try{
      
       container.reset();
       diagnostics.clear();   
        char c = content.charAt(position.getIndex() - 1);
         if (!isAutoCompleteChar(c)) {
           return;
         }
    String prefix =
        CompletionHelper.computePrefix(content, position, MyCharacter::isJavaIdentifierPart);
        CompletionParameters parameters =
        CompletionParameters.builder()
            .setColumn(position.getColumn())
            .setLine(position.getLine())
            .setIndex(position.getIndex())
            .setEditor(editor)
            .setFile(editor.getCurrentFile())
            .setProject(editor.getProject())
            .setModule(editor.getProject().getMainModule())
            .setContents(content.getReference().toString())
            .setPrefix(prefix)
            .build();
        CompletionList completionList = server.complete(parameters);
        if (completionList == null) {
            return;
        }
        publisher.setUpdateThreshold(0);
        completionList.getItems()/*.stream().map(CompletionItemWrapper::new)*/.forEach(publisher::addItem);
        }catch(Exception e){
        if (!(e instanceof InterruptedException)
                    && !(e instanceof ProcessCanceledException)) {
                    Log.e(TAG, "Completion failed", e);
        }
        }
  }
  
  public List<DiagnosticWrapper> getDiagnostics(){
     return diagnostics;
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
      analyzer.destroy();
      delegate.destroy();
   }
  
}