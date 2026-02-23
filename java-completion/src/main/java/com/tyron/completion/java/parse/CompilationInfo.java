package com.tyron.completion.java.parse;

import androidx.annotation.NonNull;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Pair;
import com.tyron.builder.model.CodeAssistLibrary;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.impl.AndroidModuleImpl;
import com.tyron.common.util.DebouncerStore;
import com.tyron.completion.java.compiler.Parser;
import com.tyron.completion.java.compiler.services.NBEnter;
import com.tyron.completion.java.compiler.services.NBLog;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.completion.java.provider.PruneMethodBodies;
import com.tyron.completion.java.util.ProjectUtil;
import dev.mutwakil.javac.MJavacTrees;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.jetbrains.kotlin.com.intellij.openapi.util.Key;

public class CompilationInfo {

  public static final Key<CompilationInfo> COMPILATION_INFO_KEY = Key.create("compilationInfo");
  private static Set<File> mCachedPaths = new HashSet<>();

  public static synchronized CompilationInfo get(Module module, boolean reIndex) {
    if (!(module instanceof JavaModule)) {
      return null;
    }
    JavaModule javaModule = (JavaModule) module;
    CompilationInfo info = module.getUserData(COMPILATION_INFO_KEY);
    if (info == null || reIndex) {
      Set<File> libraries = new HashSet<>();
      libraries.addAll(javaModule.getLibraries());

      if (module instanceof AndroidModuleImpl) {
        File kotlinJar =
            new File(
                module.getRootFile(),
                "/build/libraries/kotlin_runtime/" + module.getRootFile().getName() + ".jar");
        if (kotlinJar.exists()) {
          libraries.add(kotlinJar);
          javaModule.addLibrary(CodeAssistLibrary.forJar(kotlinJar));
        }
        Set<File> files = new HashSet<>();
        File buildGenDir = new File(module.getRootFile() + "/build/gen");
        File viewBindingDir = new File(module.getRootFile() + "/build/view_binding");
        if (buildGenDir.exists()) {
          files.addAll(getFiles(buildGenDir, ".java"));
        }
        if (viewBindingDir.exists()) {
          files.addAll(getFiles(viewBindingDir, ".java"));
        }

        for (File value : files) {
          javaModule.addJavaFile(value);
        }
      }
      info =
          new CompilationInfo(
              new CompilationInfoImpl(
                  new JavacParser(),
                  null,
                  null,
                  new ArrayList<>(libraries),
                  Collections.emptyList(),
                  null,
                  null));
      module.putUserData(COMPILATION_INFO_KEY, info);
      // info.indexJavaFiles(module);
      for (File value : javaModule.getJavaFiles().values()) {
        info.updateFile(module, value);
      }
    }
    return info;
  }

  public static CompilationInfo get(Module module) {
    return get(module, false);
  }

  public static CompilationInfo get(Project currentProject, File file) {
    return get(currentProject, file, false);
  }

  public static CompilationInfo get(Project currentProject, File file, boolean reIndex) {
    final Module module = currentProject.getModule(file);
    ProjectUtil.getInstance().setProject(currentProject).setModule(module);
    return get(module, reIndex);
  }

  private synchronized void indexJavaFiles(Module module) {
    if (module instanceof AndroidModuleImpl) {
      JavaModule javaModule = (JavaModule) module;
      Set<File> files = new HashSet<>();
      File buildGenDir = new File(module.getRootFile() + "/build/gen");
      File viewBindingDir = new File(module.getRootFile() + "/build/view_binding");
      if (buildGenDir.exists()) {
        files.addAll(getFiles(buildGenDir, ".java"));
      }
      if (viewBindingDir.exists()) {
        files.addAll(getFiles(viewBindingDir, ".java"));
      }

      for (File value : files) {
        javaModule.addJavaFile(value);
      }
      for (File value : javaModule.getJavaFiles().values()) {
        updateFile(module, value);
      }
    }
  }

  public JavacUtilitiesProvider parse() {
    return null;
  }

  public JCCompilationUnit updateFile(Module module, File file) {
    return updateImmediately(
        new SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            //            try {
            //              return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            //            } catch (IOException e) {
            Parser parser = Parser.parseFile(module.getProject(), file.toPath());
            // During indexing, statements inside methods are not needed so
            // it is stripped to speed up the index process
            return new PruneMethodBodies(impl.getJavacTask()).scan(parser.root, 0L);
            //            }
          }
        });
  }

  public void updateFile(Module module, File file, Consumer<JCCompilationUnit> treeConsumer) {
    update(
        new SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            //            try {
            //              return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            //            } catch (IOException e) {
            Parser parser = Parser.parseFile(module.getProject(), file.toPath());
            // During indexing, statements inside methods are not needed so
            // it is stripped to speed up the index process
            return new PruneMethodBodies(impl.getJavacTask()).scan(parser.root, 0L);
            //            }
          }
        },
        0,
        treeConsumer);
  }

  public final CompilationInfoImpl impl;
  private final Map<URI, JCCompilationUnit> compiledMap = new HashMap<>();

  private final DebouncerStore<String> debouncerStore = DebouncerStore.DEFAULT;

  private final Object parseLock = new Object();
  private Trees trees;

  public CompilationInfo(final CompilationInfoImpl impl) {
    assert impl != null;
    this.impl = impl;
  }

  public JCCompilationUnit updateImmediately(JavaFileObject fileObject) {
    CompletableFuture<JCCompilationUnit> future = new CompletableFuture<>();
    update(fileObject, 0, future::complete);
    try {
      return future.get();
    } catch (ExecutionException | InterruptedException e) {
      return null;
    }
  }

  public void update(JavaFileObject fileObject) {
    this.update(fileObject, 300, __ -> {});
  }

  public void update(JavaFileObject fileObject, long delay) {
    this.update(fileObject, delay, __ -> {});
  }

  public synchronized void update(
      JavaFileObject fileObject, long delay, Consumer<JCCompilationUnit> treeConsumer) {
    debouncerStore
        .registerOrGetDebouncer("update")
        .debounce(
            delay,
            () -> {
              synchronized (parseLock) {
                try {
                  JavacTaskImpl javacTask = impl.getJavacTask();

                  NBLog log = NBLog.instance(javacTask.getContext());
                  log.useSource(fileObject);

                  Set<Pair<JavaFileObject, Integer>> toRemove = new HashSet<>();
                  for (Pair<JavaFileObject, Integer> pair : log.getRecorded()) {
                    if (pair.fst.toUri().equals(fileObject.toUri())) {
                      toRemove.add(pair);
                    }
                  }
                  log.getRecorded().removeAll(toRemove);
                  log.removeDiagnostics(fileObject.toUri());
                  log.removeFileObject(fileObject);

                  JCCompilationUnit previous = compiledMap.get(fileObject.toUri());
                  if (previous != null) {
                    NBEnter enter = (NBEnter) NBEnter.instance(javacTask.getContext());
                    enter.unenter(previous, previous);
                    enter.removeCompilationUnit(fileObject);
                  }

                  // reparse the whole file
                  JCCompilationUnit unit;
                  try {
                    JavaCompiler compiler = JavaCompiler.instance(javacTask.getContext());
                    unit = compiler.parse(fileObject);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }

                  Iterable<? extends Element> enter = javacTask.enter(List.of(unit));

                  if (previous != null) {
                    unit.packge = previous.packge;
                  }

                  Iterable<? extends Element> analyze = javacTask.analyze(enter);

                  JCCompilationUnit newUnit = unit;
                  compiledMap.put(fileObject.toUri(), newUnit);

                  treeConsumer.accept(unit);
                } catch (Throwable t) {
                  System.out.println(t);
                  treeConsumer.accept(null);
                }
              }
            });
  }

  public JCCompilationUnit getCompilationUnit(JavaFileObject fileObject) {
    return getCompilationUnit(fileObject.toUri());
  }

  public JCCompilationUnit getCompilationUnit(URI uri) {
    return compiledMap.get(uri);
  }

  public List<? extends TypeElement> getTopLevelElements() throws IllegalStateException {
    return null;
    //        if (this.impl.getFileObject() == null) {
    //            throw new IllegalStateException ();
    //        }
    //        final List<TypeElement> result = new ArrayList<TypeElement>();
    //        if (this.impl.isClassFile()) {
    //            final JavacElements elements = (JavacElements) getElements();
    //            assert elements != null;
    //            assert this.impl.getRoot() != null;
    //            final String name = FileObjects.convertFolder2Package(FileObjects.stripExtension(
    //                    FileUtil.getRelativePath(this.impl.getRoot(),
    // this.impl.getFileObject())));
    //            final TypeElement e = Optional.ofNullable(
    //                            SourceVersion.RELEASE_9.compareTo(getSourceVersion()) <= 0 ?
    //                                    SourceUtils.getModuleName(impl.getRoot().toURL(), true) :
    //                                    null)
    //                    .map(elements::getModuleElement)
    //                    .map((module) -> ElementUtils.getTypeElementByBinaryName(this, module,
    // name))
    //                    .orElseGet(() -> ElementUtils.getTypeElementByBinaryName(this, name));
    //            if (e != null) {
    //                result.add (e);
    //            }
    //        } else {
    //            CompilationUnitTree cu = getCompilationUnit();
    //            if (cu == null) {
    //                return null;
    //            }
    //            else {
    //                final Trees ts = getTrees();
    //                assert ts != null;
    //                List<? extends Tree> typeDecls = cu.getTypeDecls();
    //                TreePath cuPath = new TreePath(cu);
    //                for( Tree t : typeDecls ) {
    //                    TreePath p = new TreePath(cuPath,t);
    //                    Element e = ts.getElement(p);
    //                    if ( e != null && ( e.getKind().isClass() || e.getKind().isInterface() ) )
    // {
    //                        result.add((TypeElement)e);
    //                    }
    //                }
    //            }
    //        }
    //        return Collections.unmodifiableList(result);
  }

  /**
   * Return the {@link Trees} service of the javac represented by this {@link CompilationInfo}.
   *
   * @return javac Trees service
   */
  public synchronized @NonNull Trees getTrees() {
    if (trees == null) {
      // use a working init order:
      com.sun.tools.javac.main.JavaCompiler.instance(impl.getJavacTask().getContext());
      trees = MJavacTrees.instance(impl.getJavacTask().getContext());
    }
    return trees;
  }

  /**
   * Return the {@link Elements} service of the javac represented by this {@link CompilationInfo}.
   *
   * @return javac Elements service
   */
  public @NonNull Elements getElements() {
    // use a working init order:
    com.sun.tools.javac.main.JavaCompiler.instance(impl.getJavacTask().getContext());
    return impl.getJavacTask().getElements();
  }

  public File getFileObject() {
    //        return impl.getFileObject();
    throw new UnsupportedOperationException();
  }

  public CompilationUnitTree getCompilationUnit() {
    return impl.getCompilationUnit();
  }

  private static synchronized boolean changed(Set<File> oldFiles, Set<File> newFiles) {
    if (oldFiles.size() != newFiles.size()) {
      return true;
    }

    for (File oldFile : oldFiles) {
      if (!newFiles.contains(oldFile)) {
        return true;
      }
    }

    for (File newFile : newFiles) {
      if (!oldFiles.contains(newFile)) {
        return true;
      }
    }

    return false;
  }

  public static Set<File> getFiles(File dir, String ext) {
    Set<File> Files = new HashSet<>();

    File[] files = dir.listFiles();
    if (files == null) {
      return Collections.emptySet();
    }

    for (File file : files) {
      if (file.isDirectory()) {
        Files.addAll(getFiles(file, ext));
      } else {
        if (file.getName().endsWith(ext)) {
          Files.add(file);
        }
      }
    }

    return Files;
  }
}
