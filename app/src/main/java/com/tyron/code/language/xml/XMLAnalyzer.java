package com.tyron.code.language.xml;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.incremental.resource.IncrementalAapt2Task;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.analyzer.DiagnosticTextmateAnalyzer;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.util.ProjectUtils;
import com.tyron.common.util.Debouncer;
import com.tyron.completion.java.compiler.Parser;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.PruneMethodBodies;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.completion.xml.task.InjectResourcesTask;
import com.tyron.editor.Editor;
import com.tyron.viewbinding.task.InjectViewBindingTask;
import dev.mutwakil.codeassist.BuildConfig;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import kotlin.Unit;
import org.apache.commons.io.FileUtils;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

public class XMLAnalyzer extends DiagnosticTextmateAnalyzer {

  private boolean mAnalyzerEnabled = false;
  private static final String SCOPENAME = "text.xml";

  private static final Debouncer sDebouncer =
      new Debouncer(
          Duration.ofMillis(900L),
          Executors.newScheduledThreadPool(
              1,
              new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                  ThreadGroup threadGroup = Looper.getMainLooper().getThread().getThreadGroup();
                  return new Thread(threadGroup, runnable, "XmlAnalyzer");
                }
              }));

  private final WeakReference<Editor> mEditorReference;

  public XMLAnalyzer(
      Editor editor,
      EmptyTextMateLanguage lang,
      IGrammar grammar,
      LanguageConfiguration languageConfiguration,
      ThemeRegistry theme)
      throws Exception {
    super(editor, lang, grammar, languageConfiguration, theme);
    mEditorReference = new WeakReference<>(editor);
  }

  public static XMLAnalyzer create(Editor editor, EmptyTextMateLanguage lang) {
    try {
      return new XMLAnalyzer(
          editor,
          lang,
          GrammarRegistry.getInstance().findGrammar(SCOPENAME),
          GrammarRegistry.getInstance().findLanguageConfiguration(SCOPENAME),
          ThemeRegistry.getInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void analyzeInBackground(CharSequence contents) {
    Editor editor = mEditorReference.get();
    if (editor == null) {
      return;
    }

    if (!mAnalyzerEnabled) {
      Project project = editor.getProject();
      if (project == null) {
        return;
      }

      if (project.isCompiling() || project.isIndexing()) {
        return;
      }

      ProgressManager.getInstance()
          .runLater(() -> ((CodeEditorView) editor).post(() -> editor.setAnalyzing(true)));

      sDebouncer.cancel();
      sDebouncer.schedule(
          cancel -> {
            AndroidModule mainModule = (AndroidModule) project.getMainModule();
            try {
              InjectResourcesTask.inject(project, mainModule);
              InjectViewBindingTask.inject(project, mainModule);
              ProgressManager.getInstance().runLater(() -> editor.setAnalyzing(false), 300);
            } catch (IOException e) {
              e.printStackTrace();
            }
            return Unit.INSTANCE;
          });
      return;
    }

    File currentFile = editor.getCurrentFile();
    if (currentFile == null) {
      return;
    }

    List<DiagnosticWrapper> diagnosticWrappers = new ArrayList<>();

    sDebouncer.cancel();
    sDebouncer.schedule(
        cancel -> {
          compile(
              currentFile,
              contents.toString(),
              new ILogger() {
                @Override
                public void info(DiagnosticWrapper wrapper) {
                  addMaybe(wrapper);
                }

                @Override
                public void debug(DiagnosticWrapper wrapper) {
                  addMaybe(wrapper);
                }

                @Override
                public void warning(DiagnosticWrapper wrapper) {
                  addMaybe(wrapper);
                }

                @Override
                public void error(DiagnosticWrapper wrapper) {
                  addMaybe(wrapper);
                }

                private void addMaybe(DiagnosticWrapper wrapper) {
                  if (currentFile.equals(wrapper.getSource())) {
                    diagnosticWrappers.add(wrapper);
                  }
                }
              });

          if (!cancel.invoke()) {
            ProgressManager.getInstance()
                .runLater(
                    () -> {
                      editor.setDiagnostics(
                          diagnosticWrappers.stream()
                              .filter(it -> it.getLineNumber() > 0)
                              .collect(Collectors.toList()));
                    });
          }
          return Unit.INSTANCE;
        });
  }

  private final Handler handler = new Handler();
  long delay = 1000L;
  long lastTime;

  private void compile(File file, String contents, ILogger logger) {
    boolean isResource = ProjectUtils.isResourceXMLFile(file);

    if (isResource) {
      Project project = ProjectManager.getInstance().getCurrentProject();
      if (project != null) {
        Module module = project.getResModule(file);
        if (module instanceof AndroidModule) {
          try {
            doGenerate(project, (AndroidModule) module, file, contents, logger);
          } catch (IOException | CompilationFailedException e) {
            if (BuildConfig.DEBUG) {
              Log.e("XMLAnalyzer", "Failed compiling", e);
            }
          }
        }
      }
    }
  }

  private void doGenerate(
      Project project, AndroidModule module, File file, String contents, ILogger logger)
      throws IOException, CompilationFailedException {
    if (!file.canWrite() || !file.canRead()) {
      return;
    }

    if (!module.getFileManager().isOpened(file)) {
      Log.e("XMLAnalyzer", "File is not yet opened!");
      return;
    }

    Optional<CharSequence> fileContent = module.getFileManager().getFileContent(file);
    if (!fileContent.isPresent()) {
      Log.e("XMLAnalyzer", "No snapshot for file found.");
      return;
    }

    contents = fileContent.get().toString();
    FileUtils.writeStringToFile(file, contents, StandardCharsets.UTF_8);
    IncrementalAapt2Task task = new IncrementalAapt2Task(project, module, logger, false);

    try {
      task.prepare(BuildType.DEBUG);
      task.run();
    } catch (CompilationFailedException e) {
      throw e;
    }

    // work around to refresh R.java file
    CompilationInfo info = CompilationInfo.get(module);
    if (info == null) {
      return;
    }
    File resourceClass = module.getJavaFile(module.getNameSpace() + ".R");
    info.updateImmediately(
        new SimpleJavaFileObject(resourceClass.toURI(), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            //             try{
            //              return readFile(resourceClass);
            //             }catch(IOException e){
            //             e.printStackTrace();
            Parser parser = Parser.parseFile(module.getProject(), resourceClass.toPath());
            // During indexing, statements inside methods are not needed so
            // it is stripped to speed up the index process
            return new PruneMethodBodies(info.impl.getJavacTask()).scan(parser.root, 0L);
            //           }
          }
        });
  }

  public static String readFile(File file) throws IOException {
    long length = file.length();

    StringBuilder builder =
        new StringBuilder(length > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) length);

    char[] buffer = new char[16 * 1024]; // 16KB buffer

    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
            buffer.length)) {

      int read;
      while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
        builder.append(buffer, 0, read);
      }
    }

    return builder.toString();
  }
}
