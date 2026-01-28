package com.tyron.completion.java.util;

import android.annotation.SuppressLint;
import com.tyron.builder.model.SourceFileObject;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.util.PackageTrie;
import com.tyron.common.util.Cache;
import com.tyron.common.util.StringSearch;
import com.tyron.completion.java.Docs;
import com.tyron.completion.java.FindTypeDeclarations;
import com.tyron.completion.java.compiler.SourceFileManager;
import com.tyron.completion.java.parse.CompilationInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/*
 * @author Wadamzmail
 */
public class ProjectUtil {

  private Project mProject;
  private JavaModule mCurrentModule;
  private static ProjectUtil instance;
  private Docs docs;
  private Set<File> docPath = Collections.emptySet();
  public static final Path NOT_FOUND = Paths.get("");
  public SourceFileManager mSourceFileManager;

  public static ProjectUtil getInstance() {
    if (instance == null) {
      instance = new ProjectUtil();
    }
    return instance;
  }

  private ProjectUtil() {}

  public ProjectUtil setProject(Project project) {
    if (mProject == null) {
      mProject = project;
      updateDocs();
      updateSFM();
      return instance;
    }
    if (mProject.equals(project)) return instance;
    this.mProject = project;
    updateDocs();
    updateSFM();
    return instance;
  }

  public ProjectUtil setModule(Module module) {

    if (module instanceof JavaModule) {
      if (mCurrentModule == null) {
        mCurrentModule = (JavaModule) module;
        updateDocs();
        return instance;
      }
      if (mCurrentModule.equals((JavaModule) module)) return instance;
      mCurrentModule = (JavaModule) module;
      updateDocs();
    } else {
      throw new IllegalArgumentException("Module must be a JavaModule");
    }
    return instance;
  }

  public ProjectUtil updateDocs() {
    return updateDocs(docPath);
  }

  public ProjectUtil updateDocs(Set<File> docPath) {
    this.docs = new Docs(mProject, docPath);
    return instance;
  }

  public ProjectUtil setDocsPath(Set<File> docPath) {
    if (docPath == null) return instance;
    if (docPath.equals(this.docPath)) return instance;
    this.docPath = docPath;
    return updateDocs(docPath);
  }

  public ProjectUtil updateSFM() {
    this.mSourceFileManager = new SourceFileManager(mProject);
    return instance;
  }

  public Project getProject() {
    return mProject;
  }

  public JavaModule getModule() {
    return mCurrentModule;
  }

  public Set<String> publicTopLevelTypes() {
    Set<String> classes = new HashSet<>();

    if (mProject == null || mCurrentModule == null) {
      return classes;
    }

    List<Module> deps = mProject.getDependencies(mCurrentModule);
    if (deps == null) return classes;

    for (Module module : deps) {
      if (module instanceof JavaModule) {
        classes.addAll(((JavaModule) module).getAllClasses());
      }
    }
    return classes;
  }

  public Set<String> findClasses(String packageName) {
    Set<String> classes = new HashSet<>();
    for (Module module : mProject.getDependencies(mCurrentModule)) {
      if (module instanceof JavaModule) {
        PackageTrie classIndex = ((JavaModule) module).getClassIndex();
        classes.addAll(classIndex.getMatchingPackages(packageName));
      }
    }
    return classes;
  }

  /** For suggesting the first import typed where the package names are not yet correct */
  public Set<String> getTopLevelNonLeafPackages(Predicate<String> filter) {
    Set<String> packages = new HashSet<>();
    for (Module module : mProject.getDependencies(mCurrentModule)) {
      if (module instanceof JavaModule) {
        PackageTrie classIndex = ((JavaModule) module).getClassIndex();
        for (String node : classIndex.getTopLevelNonLeafNodes()) {
          if (filter.test(node)) {
            packages.add(node);
          }
        }
      }
    }
    return packages;
  }

  /**
   * Finds all the occurrences of a class in javadocs, and source files
   *
   * @param className fully qualified name of the class
   * @return Optional of type JavaFileObject that may be empty if the file is not found
   */
  @SuppressLint("NewApi")
  public Optional<JavaFileObject> findAnywhere(String className) {
    Optional<JavaFileObject> fromDocs = findPublicTypeDeclarationInDocPath(className);
    if (fromDocs.isPresent()) {
      return fromDocs;
    }

    Path fromSource = findTypeDeclaration(className);
    if (fromSource != NOT_FOUND) {
      return Optional.of(new SourceFileObject(fromSource, mCurrentModule));
    }

    return Optional.empty();
  }

  /**
   * Searches the javadoc file manager if it contains the classes with javadoc
   *
   * @param className the fully qualified name of the class
   * @return optional of type JavaFileObject, may be empty if it doesn't exist
   */
  private Optional<JavaFileObject> findPublicTypeDeclarationInDocPath(String className) {
    try {
      JavaFileObject found =
          docs.fileManager.getJavaFileForInput(
              StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);
      return Optional.ofNullable(found);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Path findTypeDeclaration(String className) {
    Path fastFind = findPublicTypeDeclaration(className);
    if (fastFind != NOT_FOUND) {
      return fastFind;
    }

    List<Module> dependencies = mProject.getDependencies(mCurrentModule);
    String packageName = packageName(className);
    String simpleName = simpleName(className);

    for (Module dependency : dependencies) {
      Path path = findPublicTypeDeclarationInModule(dependency, packageName, simpleName, className);
      if (path != NOT_FOUND) {
        return path;
      }
    }

    return NOT_FOUND;
  }

  private Path findPublicTypeDeclarationInModule(
      Module module, String packageName, String simpleName, String className) {
    for (File file : SourceFileManager.list(module, packageName)) {
      if (containsWord(file.toPath(), simpleName) && containsType(file.toPath(), className)) {
        if (file.getName().endsWith(".java")) {
          return file.toPath();
        }
      }
    }
    return NOT_FOUND;
  }

  private Path findPublicTypeDeclaration(String className) {
    JavaFileObject source;
    try {
      source =
          mSourceFileManager.getJavaFileForInput(
              StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (source == null) {
      return NOT_FOUND;
    }
    if (!source.toUri().getScheme().equals("file")) {
      return NOT_FOUND;
    }
    Path file = Paths.get(source.toUri());
    if (!containsType(file, className)) {
      return NOT_FOUND;
    }
    return file;
  }

  public Optional<JavaFileObject> findPublicTypeDeclarationInJdk(String className) {
    JavaFileObject source;
    try {
      source =
          mSourceFileManager.getJavaFileForInput(
              StandardLocation.PLATFORM_CLASS_PATH, className, JavaFileObject.Kind.CLASS);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return Optional.ofNullable(source);
  }

  private static final Pattern PACKAGE_EXTRACTOR =
      Pattern.compile("^([a-z][_a-zA-Z0-9]*\\.)*[a-z][_a-zA-Z0-9]*");

  private String packageName(String className) {
    Matcher m = PACKAGE_EXTRACTOR.matcher(className);
    if (m.find()) {
      return m.group();
    }
    return "";
  }

  private static final Pattern SIMPLE_EXTRACTOR = Pattern.compile("[A-Z][_a-zA-Z0-9]*$");

  private String simpleName(String className) {
    Matcher m = SIMPLE_EXTRACTOR.matcher(className);
    if (m.find()) {
      return m.group();
    }
    return "";
  }

  private static final Cache<String, Boolean> cacheContainsWord = new Cache<>();

  private boolean containsWord(Path file, String word) {
    if (cacheContainsWord.needs(file, word)) {
      cacheContainsWord.load(file, word, StringSearch.containsWord(file, word));
    }
    return cacheContainsWord.get(file, word);
  }

  private static final Cache<Void, List<String>> cacheContainsType = new Cache<>();

  private boolean containsType(Path file, String className) {
    if (cacheContainsType.needs(file, null)) {
      List<String> types = new ArrayList<>();
      var unit = CompilationInfo.get(getModule()).updateFile(getModule(), file.toFile());
      new FindTypeDeclarations().scan(unit, types);
      cacheContainsType.load(file, null, types);
    }
    return cacheContainsType.get(file, null).contains(className);
  }
}
