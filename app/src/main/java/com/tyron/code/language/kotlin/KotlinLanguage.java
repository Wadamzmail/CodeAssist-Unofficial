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

public class KotlinLanguage extends EmptyTextMateLanguage implements Language {

    private static final String GRAMMAR_NAME = "kotlin.tmLanguage";
    private static final String LANGUAGE_PATH = "textmate/kotlin/syntaxes/kotlin.tmLanguage";
    private static final String CONFIG_PATH = "textmate/kotlin/language-configuration.json";
    private static final String SCOPE_NAME = "source.kotlin";
    public static final String TAG = "KotlinLanguage";

    private final TextMateLanguage delegate;
    private final Editor editor;
    public boolean createIdentifiers = false;
    private final DiagnosticsContainer container = new DiagnosticsContainer();
    private final List<DiagnosticWrapper> diagnostics = new ArrayList<>();
    private Thread analysisThread;
    private volatile boolean analysisRunning = true;
    private final CachedAutoCompleteProvider autoCompleteProvider;
     
    
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

    public KotlinEnvironment kotlinEnvironment;

    public KotlinLanguage(Editor editor) {
        this.editor = editor;
        delegate = LanguageManager.createTextMateLanguage(SCOPE_NAME);
        autoCompleteProvider = new CachedAutoCompleteProvider(editor,
                new KotlinAutoCompleteProvider(editor)); 
        initEnv();
    }
    
    private boolean isHighlightEnabled(){
      return ApplicationLoader.getDefaultPreferences().getBoolean(SharedPreferenceKeys.KOTLIN_HIGHLIGHTING, false);
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
       try{
       container.reset();
       diagnostics.clear();   
       CompletionList completionList = autoCompleteProvider.getCompletionList(null,
                position.getLine(),
                position.getColumn());
        if (completionList == null) {
            return;
        }
        Objects.requireNonNull((CodeEditorView)editor).post(() -> ((CodeEditorView)editor).setDiagnostics(container));
        publisher.setUpdateThreshold(0);
        completionList.getItems()/*.stream().map(CompletionItemWrapper::new)*/.forEach(publisher::addItem);
        }catch(Exception e){
        if (!(e instanceof InterruptedException)
                    && !(e instanceof ProcessCanceledException)) {
                    Log.e(TAG, "Completion failed", e);
        }
        }
//        Objects.requireNonNull((CodeEditorView)editor).post(() -> ((CodeEditorView)editor).setDiagnostics(new ArrayList<DiagnosticWrapper>(kotlinEnvironment.getDiagnostics())));           
        kotlinEnvironment.analysis = null;  
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
      destroyAnalysis();
      delegate.destroy();
   }

   private void destroyAnalysis(){
      analysisRunning = false; 
      if (analysisThread != null && analysisThread.isAlive()) {
        analysisThread.interrupt();  
        try {
            analysisThread.join();  
        } catch (InterruptedException ignore) {}
       }
   }

   public void initEnv(){
     Project project = ProjectManager.getInstance().getCurrentProject();
     if(project==null || editor.getCurrentFile()==null)return;
        Module currentModule = project.getModule(editor.getCurrentFile());
        kotlinEnvironment = KotlinEnvironment.Companion.get(currentModule);
        if(isHighlightEnabled()){
             initAnalysis();
        }
   }
    
   private void initAnalysis() {
    destroyAnalysis(); 
    analysisRunning = true;
    analysisThread = new Thread(() -> {
        kotlinEnvironment.addIssueListener(issue -> {
            if (!analysisRunning) return kotlin.Unit.INSTANCE;
            if (editor==null) return kotlin.Unit.INSTANCE;
            if (!isHighlightEnabled()) return kotlin.Unit.INSTANCE;

            short severity;
            CompilerMessageSeverity s = issue.getSeverity();
            
            DiagnosticWrapper wrapper = new DiagnosticWrapper();
            wrapper.setStartPosition(issue.getStartOffset());
            wrapper.setEndPosition(issue.getEndOffset());
            wrapper.setMessage(issue.getMessage());
            wrapper.setKind(KotlinSeverityMapper.toKind(issue.getSeverity()));
            if (wrapper.getKind() == null) return kotlin.Unit.INSTANCE;
            diagnostics.add(wrapper);

            if (s == CompilerMessageSeverity.ERROR) {
                severity = DiagnosticRegion.SEVERITY_ERROR;
            } else if (s == CompilerMessageSeverity.WARNING
                    || s == CompilerMessageSeverity.STRONG_WARNING) {
                severity = DiagnosticRegion.SEVERITY_WARNING;
            } else {
                return kotlin.Unit.INSTANCE;
            }
            
           if (!analysisRunning) return kotlin.Unit.INSTANCE;

            Objects.requireNonNull((CodeEditorView) editor).post(() -> {
                container.addDiagnostic(
                        new DiagnosticRegion(
                                issue.getStartOffset(),
                                issue.getEndOffset(),
                                severity, 
                                0,
                                null//new DiagnosticDetail("Info",issue.getMessage(),null,null)
                        )
                );
            });
            return kotlin.Unit.INSTANCE;
        });
      
        if (!analysisRunning) return;
        if (!isHighlightEnabled()) return;
        if (editor==null) return;
        if (editor.getCurrentFile()==null)return;

        var fileEntry = kotlinEnvironment.kotlinFiles.get(editor.getCurrentFile().getAbsolutePath());
        if (fileEntry == null) return;

        var ktFile = fileEntry.getKotlinFile();

        try {
            if (!analysisRunning) return;

             kotlinEnvironment.analysisOf(
                    kotlinEnvironment.kotlinFiles.values().stream()
                            .map(it -> it.getKotlinFile())
                            .toList(),
                    ktFile
            );

            if (!analysisRunning) return;

            Objects.requireNonNull((CodeEditorView) editor)
                    .post(() -> ((CodeEditorView) editor).setDiagnostics(container));

        } catch (Throwable e) {
            if (!(e instanceof InterruptedException)
                    && !(e instanceof ProcessCanceledException)) {
                 Log.e(TAG, "Failed to analyze file", e);
            }
        }
    });
    analysisThread.start();
  }
  
}