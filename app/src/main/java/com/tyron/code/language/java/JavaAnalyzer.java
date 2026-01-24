package com.tyron.code.language.java;

import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.model.SourceFileObject;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.SemanticAnalyzeManager;
import com.tyron.code.analyzer.semantic.SemanticToken;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.util.Debouncer;
import com.tyron.completion.java.compiler.services.NBLog;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.util.ErrorCodes;
import com.tyron.completion.java.util.TreeUtil;
import com.tyron.completion.progress.ProcessCanceledException;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.editor.Editor;
import dev.mutwakil.codeassist.BuildConfig;
import dev.mutwakil.javac.*;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import java.io.File;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

public class JavaAnalyzer extends SemanticAnalyzeManager {

  private static final String GRAMMAR_NAME = "java.tmLanguage.json";
  private static final String LANGUAGE_PATH = "textmate/java/syntaxes/java.tmLanguage.json";
  private static final String CONFIG_PATH = "textmate/java/language-configuration.json";
  private static final String SCOPENAME = "source.java";

  public static JavaAnalyzer create(Editor editor, EmptyTextMateLanguage lang) {
    try {
      return new JavaAnalyzer(
          editor,
          lang,
          GrammarRegistry.getInstance().findGrammar(SCOPENAME),
          GrammarRegistry.getInstance().findLanguageConfiguration(SCOPENAME),
          ThemeRegistry.getInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final Debouncer sDebouncer =
      new Debouncer(
          Duration.ofMillis(700),
          Executors.newScheduledThreadPool(
              1,
              new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                  ThreadGroup threadGroup = Looper.getMainLooper().getThread().getThreadGroup();
                  return new Thread(threadGroup, runnable, TAG);
                }
              }));
  private static final String TAG = JavaAnalyzer.class.getSimpleName();

  private final WeakReference<Editor> mEditorReference;
  private final SharedPreferences mPreferences;

  public JavaAnalyzer(
      Editor editor,
      EmptyTextMateLanguage lang,
      IGrammar grammar,
      LanguageConfiguration languageConfiguration,
      ThemeRegistry theme)
      throws Exception {
    super(editor, lang, grammar, languageConfiguration, theme);

    mEditorReference = new WeakReference<>(editor);
    mPreferences = ApplicationLoader.getDefaultPreferences();
  }

  @Override
  public List<SemanticToken> analyzeSpansAsync(CharSequence contents) {
    Editor editor = mEditorReference.get();
    Project project = editor.getProject();
    File currentFile = editor.getCurrentFile();

    CompilationInfo info = getInfo(editor);
    if (info == null) {
      return null;
    }

    SourceFileObject object =
        new SourceFileObject(currentFile.toPath(), contents.toString(), Instant.now());
    CompletableFuture<List<SemanticToken>> future = new CompletableFuture<>();
    info.update(
        object,
        0,
        unit -> {
          JavacTaskImpl task = info.impl.getJavacTask();
          JavaSemanticHighlighter highlighter = new JavaSemanticHighlighter(task);
          CompilationUnitTree root = info.getCompilationUnit();
          highlighter.scan(root, true);
          future.complete(highlighter.getTokens());
        });
    try {
      return future.get();
    } catch (ExecutionException | InterruptedException e) {
      e.printStackTrace();
      return new ArrayList<SemanticToken>();
    }
  }

  @Override
  public void analyzeInBackground(CharSequence contents) {
    sDebouncer.cancel();
    sDebouncer.schedule(
        cancel -> {
          doAnalyzeInBackground(cancel, contents);
          return Unit.INSTANCE;
        });
  }

  private CompilationInfo getInfo(Editor editor) {
    Project project = ProjectManager.getInstance().getCurrentProject();
    if (project == null) {
      return null;
    }
    if (project.isCompiling() || project.isIndexing()) {
      return null;
    }
    Module module = project.getModule(editor.getCurrentFile());
    CompilationInfo compilationInfo = CompilationInfo.get(module);
    return compilationInfo;
  }

  private void doAnalyzeInBackground(Function0<Boolean> cancel, CharSequence contents) {
    Log.d(TAG, "doAnalyzeInBackground: called");
    Editor editor = mEditorReference.get();
    if (editor == null) {
      return;
    }
    if (cancel.invoke()) {
      return;
    }

    // do not compile the file if it not yet closed as it will cause issues when
    // compiling multiple files at the same time
    if (mPreferences.getBoolean(SharedPreferenceKeys.JAVA_ERROR_HIGHLIGHTING, true)) {
      CompilationInfo info = getInfo(editor);
      if (info != null) {
        File currentFile = editor.getCurrentFile();
        if (currentFile == null) {
          return;
        }
        Module module = ProjectManager.getInstance().getCurrentProject().getModule(currentFile);
        if (!module.getFileManager().isOpened(currentFile)) {
          return;
        }
        try {
          ProgressManager.getInstance().runLater(() -> editor.setAnalyzing(true));
          SourceFileObject sourceFileObject =
              new SourceFileObject(currentFile.toPath(), contents.toString(), Instant.now());
          info.update(
              sourceFileObject,
              0,
              unit -> {
                JavacTaskImpl task = info.impl.getJavacTask();
                if (!cancel.invoke()) {
                  List<DiagnosticWrapper> collect =
                      new ArrayList<JCDiagnostic>(
                              NBLog.instance(task.getContext()).getDiagnostics(currentFile.toURI()))
                          .stream()
                              .map(d -> modifyDiagnostic(info, d))
                              .peek(it -> ProgressManager.checkCanceled())
                              .filter(d -> currentFile.equals(d.getSource()))
                              .collect(Collectors.toList());
                  editor.setDiagnostics(collect);

                  ProgressManager.getInstance().runLater(() -> editor.setAnalyzing(false), 300);
                }
              });
        } catch (Throwable e) {
          if (e instanceof ProcessCanceledException) {
            throw e;
          }
          if (BuildConfig.DEBUG) {
            Log.e(TAG, "Unable to get diagnostics", e);
          }
          ProgressManager.getInstance().runLater(() -> editor.setAnalyzing(false));
        }
      }
    }
  }

  private DiagnosticWrapper modifyDiagnostic(
      CompilationInfo info, Diagnostic<? extends JavaFileObject> diagnostic) {
    DiagnosticWrapper wrapped = new DiagnosticWrapper(diagnostic);
    JavacTaskImpl task = info.impl.getJavacTask();
    if (diagnostic instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper) {
      // Trees trees = Trees.instance(task.task);
      Trees trees = MTrees.instance(task);
      SourcePositions positions = trees.getSourcePositions();

      JCDiagnostic jcDiagnostic = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) diagnostic).d;
      JCDiagnostic.DiagnosticPosition diagnosticPosition = jcDiagnostic.getDiagnosticPosition();
      JCTree tree = diagnosticPosition.getTree();

      if (tree != null) {
        TreePath treePath = trees.getPath(info.getCompilationUnit(), tree);
        if (treePath == null) {
          return wrapped;
        }
        String code = jcDiagnostic.getCode();

        long start = diagnostic.getStartPosition();
        long end = diagnostic.getEndPosition();
        switch (code) {
          case ErrorCodes.MISSING_RETURN_STATEMENT:
            TreePath block = TreeUtil.findParentOfType(treePath, BlockTree.class);
            if (block != null) {
              // show error span only at the end parenthesis
              end = positions.getEndPosition(info.getCompilationUnit(), block.getLeaf()) + 1;
              start = end - 2;
            }
            break;
          case ErrorCodes.DEPRECATED:
            if (treePath.getLeaf().getKind() == Tree.Kind.METHOD) {
              MethodTree methodTree = (MethodTree) treePath.getLeaf();
              if (methodTree.getBody() != null) {
                start = positions.getStartPosition(info.getCompilationUnit(), methodTree);
                end = positions.getStartPosition(info.getCompilationUnit(), methodTree.getBody());
              }
            }
            break;
        }

        wrapped.setStartPosition(start);
        wrapped.setEndPosition(end);
      }
    }
    return wrapped;
  }
}
