package com.tyron.builder.project.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.tyron.builder.model.CodeAssistAndroidLibrary;
import com.tyron.builder.model.CodeAssistLibrary;
import com.tyron.builder.project.api.ContentRoot;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.util.PackageTrie;
import com.tyron.common.util.StringSearch;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jetbrains.kotlin.com.intellij.util.ReflectionUtil;

public class JavaModuleImpl extends ModuleImpl implements JavaModule {

  // Map of fully qualified names and the jar they are contained in
  private final Map<String, File> mClassFiles;
  private final Map<String, File> mJavaFiles;
  private final Map<String, CodeAssistLibrary> mLibraryHashMap;
  private final Map<String, File> mInjectedClassesMap;
  protected final Set<File> mLibraries;
  private final Set<File> mNativeLibraries;

  // the index of all the class files in this module
  private final PackageTrie mClassIndex = new PackageTrie();
  private final PackageTrie apiClassIndex = new PackageTrie();

  protected final List<CodeAssistLibrary> libraries = new ArrayList<>();
  protected final Map<String, File> mKotlinFiles;
  private final Set<ContentRoot> contentRoots = new HashSet<>(3);

  public JavaModuleImpl(File root, boolean fromChild) {
    super(root);
    mJavaFiles = new HashMap<>();
    mClassFiles = new HashMap<>();
    mLibraries = new HashSet<>();
    mNativeLibraries = new HashSet<>();
    mInjectedClassesMap = new HashMap<>();
    mLibraryHashMap = new HashMap<>();
    mKotlinFiles = new HashMap<>();

    File contentRootDirectory = new File(getRootFile(), "src/main");
    if (!fromChild) {
      ContentRoot contentRoot = new ContentRoot(contentRootDirectory);
      contentRoot.addSourceDirectory(new File("src/main/java"));
      contentRoot.addSourceDirectory(new File("src/main/kotlin"));
      addContentRoot(contentRoot);
    }
  }

  public JavaModuleImpl(File root) {
    this(root, false);
  }

  @NonNull
  @Override
  public PackageTrie getClassIndex() {
    return mClassIndex;
  }

  @NonNull
  @Override
  public PackageTrie getApiClassIndex() {
    return apiClassIndex;
  }

  @NonNull
  @Override
  public Map<String, File> getJavaFiles() {
    return mJavaFiles;
  }

  @Override
  public Set<String> getModuleDependencies() {
    // return moduleDependencies;
    return getAllProjects();
  }

  @Nullable
  @Override
  public File getJavaFile(@NonNull String packageName) {
    return mJavaFiles.get(packageName);
  }

  @Override
  public void removeJavaFile(@NonNull String packageName) {
    mJavaFiles.remove(packageName);
    mClassIndex.remove(packageName);
    apiClassIndex.remove(packageName);
  }

  @Override
  public void addJavaFile(@NonNull File javaFile) {
    if (!javaFile.getName().endsWith(".java")) {
      return;
    }
    String className = getFullyQualifiedName(javaFile);
    mJavaFiles.put(className, javaFile);
    mClassIndex.add(className);
    apiClassIndex.add(className);
  }

  @Override
  public void putLibraryHashes(Map<String, CodeAssistLibrary> hashes) {
    mLibraryHashMap.putAll(hashes);
  }

  @Nullable
  @Override
  public CodeAssistLibrary getLibrary(String hash) {
    return mLibraryHashMap.get(hash);
  }

  @Override
  public Set<String> getAllClasses() {
    Set<String> classes = new HashSet<>();
    classes.addAll(mJavaFiles.keySet());
    classes.addAll(mClassFiles.keySet());
    classes.addAll(mInjectedClassesMap.keySet());
    classes.addAll(mKotlinFiles.keySet());
    return classes;
  }

  @Override
  public List<File> getLibraries() {
    Map<Long, File> fileSizeMap = new HashMap<>();
    Set<File> removed = new HashSet<>();

    // Calculate file sizes and store them in a map
    for (File file : mLibraries) {
      try {
        if (file.exists()) {
          long fileSize = Files.size(file.toPath());
          fileSizeMap.put(fileSize, file);
        } else {
          removed.add(file);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // Remove duplicates based on file size
    List<File> uniqueLibraryFiles = fileSizeMap.values().stream().collect(Collectors.toList());

    List<File> filteredLibraryFiles =
        uniqueLibraryFiles.stream()
            .filter(
                file -> {
                  String name = file.getParentFile().getName();
                  return !getExcludedClassPaths().contains(name);
                })
            .collect(Collectors.toList());
    for (File rm : removed) {
      mLibraries.remove(rm);
    }
    return ImmutableList.copyOf(filteredLibraryFiles);
  }

  @Override
  public List<File> getNativeLibraries() {
    Map<Long, File> fileSizeMap = new HashMap<>();
    Set<File> removed = new HashSet<>();

    // Calculate file sizes and store them in a map
    for (File file : mNativeLibraries) {
      try {
        if (file.exists()) {
          long fileSize = Files.size(file.toPath());
          fileSizeMap.put(fileSize, file);
        } else {
          removed.add(file);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // Remove duplicates based on file size
    List<File> uniqueLibraryFiles = fileSizeMap.values().stream().collect(Collectors.toList());
    for (File rm : removed) {
      mLibraries.remove(rm);
    }
    return ImmutableList.copyOf(uniqueLibraryFiles);
  }

  @Override
  public List<File> getLibraries(File dir) {
    List<File> libraries = new ArrayList<>();
    File[] libs = dir.listFiles(File::isDirectory);
    if (libs != null) {
      for (File directory : libs) {
        File check = new File(directory, "classes.jar");
        if (check.exists()) {
          libraries.add(check);
        }
      }
    }
    return libraries;
  }

  @Override
  public void addLibrary(@NonNull File libFolder) {
    boolean isAar = false;
    File check = new File(libFolder, "classes.jar");
    File checkResFolder = new File(libFolder, "res");
    File checkResStaticFolder = new File(libFolder, "res.apk");
    File checkSymbolFile = new File(libFolder, "R.txt");
    File checkPublicRes = new File(libFolder, "public.txt");
    List<File> jars = getJars(libFolder);
    CodeAssistAndroidLibrary lib = new CodeAssistAndroidLibrary();
    lib.setDeclaration(libFolder.getName());

    if (checkResFolder.exists() && checkResFolder.isDirectory()) {
      isAar = true;
      lib.setResFolder(checkResFolder);
    }
    if (checkResStaticFolder.exists()) {
      // isAar = true;
      lib.setResStaticLibrary(checkResStaticFolder);
    }
    if (checkSymbolFile.exists()) {
      // isAar = true;
      lib.setSymbolFile(checkSymbolFile);
    }
    if (checkPublicRes.exists()) {
      //     isAar = true;
      lib.setPublicResources(checkPublicRes);
    }
    if (!jars.isEmpty()) {
      if (false) {
        lib.setCompileJarFiles(jars);
        addLibrary(lib);
      } else {
        //    jars.forEach(it->addLibrary(CodeAssistLibrary.forJar(it)));
        if (check.exists()) {
          addLibrary(CodeAssistLibrary.forJar(check));
        }
      }
    }
  }

  public void addApiLibrary(@NonNull File libFolder) {
    boolean isAar = false;
    File check = new File(libFolder, "classes.jar");
    File checkResFolder = new File(libFolder, "res");
    File checkResStaticFolder = new File(libFolder, "res.apk");
    File checkSymbolFile = new File(libFolder, "R.txt");
    File checkPublicRes = new File(libFolder, "public.txt");
    List<File> jars = getJars(libFolder);
    CodeAssistAndroidLibrary lib = new CodeAssistAndroidLibrary();
    lib.setDeclaration(libFolder.getName());

    if (checkResFolder.exists() && checkResFolder.isDirectory()) {
      isAar = true;
      lib.setResFolder(checkResFolder);
    }
    if (checkResStaticFolder.exists()) {
      //     isAar = true;
      lib.setResStaticLibrary(checkResStaticFolder);
    }
    if (checkSymbolFile.exists()) {
      // isAar = true;
      lib.setSymbolFile(checkSymbolFile);
    }
    if (checkPublicRes.exists()) {
      //     isAar = true;
      lib.setPublicResources(checkPublicRes);
    }
    if (!jars.isEmpty()) {
      if (isAar) {
        lib.setCompileJarFiles(jars);
        addApiLibrary(lib);
      } else {
        //    jars.forEach(it->addApiLibrary(CodeAssistLibrary.forJar(it)));
        if (check.exists()) {
          addApiLibrary(CodeAssistLibrary.forJar(check));
        }
      }
    }
  }

  private List<File> getJars(File dir) {

    File[] jarFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));

    List<File> jars = jarFiles == null ? new ArrayList<>() : Arrays.asList(jarFiles);
    return jars;
  }

  @Override
  public void addLibrary(@NonNull CodeAssistLibrary library) {
    File jar = library.getSourceFile();
    if (jar == null) {
      return;
    }

    try {
      if (!hasClassFiles(jar)) {
        return;
      }
    } catch (IOException e) {
      // ignored, don't put the jar
    }

    if (!jar.getName().endsWith(".jar")) {
      return;
    }
    try {
      // noinspection unused, used to check if jar is valid.
      JarFile jarFile = new JarFile(jar);
      putJar(jar);
      mLibraries.add(jar);
      libraries.add(library);
    } catch (IOException e) {
      // ignored, don't put the jar
    }
  }

  public void addApiLibrary(@NonNull CodeAssistLibrary library) {
    File jar = library.getSourceFile();
    if (jar == null) {
      return;
    }

    try {
      if (!hasClassFiles(jar)) {
        return;
      }
    } catch (IOException e) {
      // ignored, don't put the jar
    }

    if (!jar.getName().endsWith(".jar")) {
      return;
    }
    try {
      // noinspection unused, used to check if jar is valid.
      JarFile jarFile = new JarFile(jar);
      putApiJar(jar);
      mLibraries.add(jar);
      libraries.add(library);
    } catch (IOException e) {
      // ignored, don't put the jar
    }
  }

  protected boolean hasClassFiles(File file) throws IOException {
    if (file == null) {
      return false;
    }
    try (JarFile jar = new JarFile(file)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();

        if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
          return true; // Found at least one .class file
        }
      }
      return false; // No .class files found
    }
  }

  protected void putJar(File file) throws IOException {
    if (file == null) {
      return;
    }
    try (JarFile jar = new JarFile(file)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();

        if (!entry.getName().endsWith(".class")) {
          continue;
        }

        // We only want top level classes, if it contains $ then
        // its an inner class, we ignore it
        if (entry.getName().contains("$")) {
          continue;
        }

        String packageName =
            entry
                .getName()
                .replace("/", ".")
                .substring(0, entry.getName().length() - ".class".length());

        mClassFiles.put(packageName, file);
        mClassIndex.add(packageName);
      }
    }
  }

  protected void putApiJar(File file) throws IOException {
    if (file == null) {
      return;
    }
    try (JarFile jar = new JarFile(file)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();

        if (!entry.getName().endsWith(".class")) {
          continue;
        }

        // We only want top level classes, if it contains $ then
        // its an inner class, we ignore it
        if (entry.getName().contains("$")) {
          continue;
        }

        String packageName =
            entry
                .getName()
                .replace("/", ".")
                .substring(0, entry.getName().length() - ".class".length());

        mClassFiles.put(packageName, file);
        mClassIndex.add(packageName);
        apiClassIndex.add(packageName);
      }
    }
  }

  @NonNull
  @Override
  public File getResourcesDir() {
    File custom = getPathSetting("java_resources_directory");
    if (custom.exists()) {
      return custom;
    }
    return new File(getRootFile(), "src/main/resources");
  }

  @NonNull
  @Override
  public File getJavaDirectory() {
    File custom = getPathSetting("java_directory");
    if (custom.exists()) {
      return custom;
    }
    return new File(getRootFile(), "src/main/java");
  }

  @Override
  public File getLibraryDirectory() {
    File custom = getPathSetting("library_directory");
    if (custom.exists()) {
      return custom;
    }
    return new File(getRootFile(), "libs");
  }

  @Override
  public File getLambdaStubsJarFile() {
    try {
      Method getLambdaStubs =
          ReflectionUtil.getDeclaredMethod(
              Class.forName("com.tyron.builder.BuildModule"), "getLambdaStubs");
      return (File) getLambdaStubs.invoke(null);
    } catch (Throwable e) {
      throw new Error(e);
    }
  }

  @Override
  public File getBootstrapJarFile() {
    try {
      Method getLambdaStubs =
          ReflectionUtil.getDeclaredMethod(
              Class.forName("com.tyron.builder.BuildModule"), "getAndroidJar");
      return (File) getLambdaStubs.invoke(null);
    } catch (Throwable e) {
      throw new Error(e);
    }
  }

  @Override
  public Map<String, File> getInjectedClasses() {
    return ImmutableMap.copyOf(mInjectedClassesMap);
  }

  @Override
  public void addInjectedClass(@NonNull File javaFile) {
    if (!javaFile.getName().endsWith(".java")) {
      return;
    }

    String className = getFullyQualifiedName(javaFile);
    mInjectedClassesMap.put(className, javaFile);
  }

  private static String getFullyQualifiedName(@NonNull File javaFile) {
    String packageName = StringSearch.packageName(javaFile);
    String className;
    if (packageName == null) {
      className = javaFile.getName().replace(".java", "");
    } else {
      className = packageName + "." + javaFile.getName().replace(".java", "");
    }
    return className;
  }

  @Override
  public void open() throws IOException {
    super.open();
  }

  @Override
  public void index() {
    try {
      putJar(getBootstrapJarFile());
    } catch (IOException e) {
      // ignored
    }

    Consumer<File> kotlinConsumer = this::addKotlinFile;

    if (getJavaDirectory().exists()) {
      FileUtils.iterateFiles(
              getJavaDirectory(),
              FileFilterUtils.suffixFileFilter(".java"),
              TrueFileFilter.INSTANCE)
          .forEachRemaining(this::addJavaFile);
    }
    if (getKotlinDirectory().exists()) {
      FileUtils.iterateFiles(
              getKotlinDirectory(),
              FileFilterUtils.suffixFileFilter(".kt"),
              TrueFileFilter.INSTANCE)
          .forEachRemaining(kotlinConsumer);
      FileUtils.iterateFiles(
              getKotlinDirectory(),
              FileFilterUtils.suffixFileFilter(".java"),
              TrueFileFilter.INSTANCE)
          .forEachRemaining(this::addJavaFile);
    }

    File[] implementation_files =
        new File(getBuildDirectory(), "libraries/implementation_files/libs")
            .listFiles(File::isDirectory);
    if (implementation_files != null) {
      for (File directory : implementation_files) {
        addLibrary(directory);
        //        File check = new File(directory, "classes.jar");
        //        if (check.exists()) {
        //          addLibrary(CodeAssistLibrary.forJar(check));
        //        }
      }
    }

    File[] implementation_libs =
        new File(getBuildDirectory(), "libraries/implementation_libs").listFiles(File::isDirectory);
    if (implementation_libs != null) {
      for (File directory : implementation_libs) {
        addLibrary(directory);
        //        File check = new File(directory, "classes.jar");
        //        if (check.exists()) {
        //          addLibrary(CodeAssistLibrary.forJar(check));
        //        }
      }
    }

    File[] natives_libs =
        new File(getBuildDirectory(), "libraries/natives_libs").listFiles(File::isDirectory);
    if (natives_libs != null) {
      for (File directory : natives_libs) {
        addLibrary(directory);
        //        File check = new File(directory, "classes.jar");
        //        if (check.exists()) {
        //          addLibrary(CodeAssistLibrary.forJar(check));
        //        }
      }
    }

    if (!getModuleName().equals("app")) {
      File[] api_files =
          new File(getBuildDirectory(), "libraries/api_files/libs").listFiles(File::isDirectory);
      if (implementation_files != null) {
        for (File directory : implementation_files) {
          addLibrary(directory);
          //        File check = new File(directory, "classes.jar");
          //        if (check.exists()) {
          //          addLibrary(CodeAssistLibrary.forJar(check));
          //        }
        }
      }

      File[] api_libs =
          new File(getBuildDirectory(), "libraries/api_libs").listFiles(File::isDirectory);
      if (implementation_libs != null) {
        for (File directory : implementation_libs) {
          addLibrary(directory);
          //        File check = new File(directory, "classes.jar");
          //        if (check.exists()) {
          //          addApiLibrary(CodeAssistLibrary.forJar(check));
          //        }
        }
      }
    }
  }

  @NonNull
  @Override
  public Map<String, File> getKotlinFiles() {
    return ImmutableMap.copyOf(mKotlinFiles);
  }

  @NonNull
  @Override
  public File getKotlinDirectory() {
    File custom = getPathSetting("kotlin_directory");
    if (custom.exists()) {
      return custom;
    }
    return new File(getRootFile(), "src/main/kotlin");
  }

  @Nullable
  @Override
  public File getKotlinFile(String packageName) {
    return mKotlinFiles.get(packageName);
  }

  @Override
  public void addKotlinFile(File file) {
    String packageName = StringSearch.packageName(file);
    if (packageName == null) {
      packageName = "";
    }
    String fqn = packageName + "." + file.getName().replace(".kt", "");
    mKotlinFiles.put(fqn, file);
  }

  @Override
  public void addContentRoot(ContentRoot contentRoot) {
    contentRoots.add(contentRoot);
  }

  @Override
  public Set<ContentRoot> getContentRoots() {
    return contentRoots;
  }

  @Override
  public void clear() {
    mJavaFiles.clear();
    mLibraries.clear();
    mNativeLibraries.clear();
    mLibraryHashMap.clear();
  }
}
