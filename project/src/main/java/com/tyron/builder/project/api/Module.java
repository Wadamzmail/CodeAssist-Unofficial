package com.tyron.builder.project.api;

import com.tyron.builder.model.ModuleSettings;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.cache.CacheHolder;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolderEx;

public interface Module extends UserDataHolderEx, CacheHolder {

  ModuleSettings getSettings();

  FileManager getFileManager();

  File getRootFile();

  File getGradleFile();

  File getSettingsGradleFile();

  File getProjectDir();

  int getMinSdk();

  String getMainClass();

  List<String> getExcludedClassPaths();

  List<String> getPlugins();

  List<String> getPlugins(File file);

  Set<String> getAllProjects();

  Set<String> getAllProjects(File file);

  Set<String> getApiProjects();

  Set<String> getApiProjects(File file);

  List<String> getIncludedProjects();

  List<AbstractMap.SimpleEntry<String, ArrayList<String>>> extractDirAndIncludes(String scope);

  List<AbstractMap.SimpleEntry<String, ArrayList<String>>> extractDirAndIncludes(
      File file, String scope);

  AbstractMap.SimpleEntry<ArrayList<String>, ArrayList<String>> extractListDirAndIncludes(
      String scope);

  AbstractMap.SimpleEntry<ArrayList<String>, ArrayList<String>> extractListDirAndIncludes(
      File file, String scope);

  default String getName() {
    return getRootFile().getName();
  }

  /**
   * Start parsing the project contents such as manifest data, project settings, etc.
   *
   * <p>Implementations may throw an IOException if something went wrong during parsing
   */
  void open() throws IOException;

  /** Remove all the indexed files */
  void clear();

  void index();

  public String getModuleName();

  // return the module path since project root path
  default String getModulePath() {
    return getModuleName().replace(":", "/");
  }

  void setModuleName(String name);

  default List<Module> getSubprojects() {
    return Collections.emptyList();
  }

  /**
   * @return The directory that this project can use to compile files
   */
  File getBuildDirectory();

  File getBuildClassesDirectory();

  // NEW API

  default void addChildModule(Module module) {}

  default Set<String> getModuleDependencies() {
    return getAllProjects();
  }

  default void addContentRoot(ContentRoot contentRoot) {}

  default Set<ContentRoot> getContentRoots() {
    return Collections.emptySet();
  }

  /**
   * @return The project that this module is part of
   */
  default Project getProject() {
    throw new UnsupportedOperationException();
  }

  default void setProject(Project project) {
    throw new UnsupportedOperationException();
  }
}
