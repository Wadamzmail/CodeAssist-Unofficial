package com.tyron.builder.project;

import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.tyron.builder.model.ProjectSettings;
import com.tyron.builder.project.api.ContentRoot;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.impl.AndroidModuleImpl;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.AndroidContentRoot;
import com.tyron.builder.project.mock.MockAndroidModule;
import com.tyron.code.event.EventManager;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class Project {

  private final Module EMPTY = new MockAndroidModule(null, null);

  private final Map<String, Module> mModules;
  private final File mRoot;
  private String mName = "app";

  private final ProjectSettings mSettings;

  private volatile boolean mCompiling;
  private volatile boolean mIndexing;

  private final Module mMainModule;
  MutableGraph<Module> graph = GraphBuilder.directed().allowsSelfLoops(false).build();

  private final EventManager eventManager;

  public Project(File root) {
    this(root, "app");
  }

  public Project(File root, String name) {
    mRoot = root;
    this.mName = name;
    mModules = new LinkedHashMap<>();
    eventManager = new EventManager();
    mMainModule = new AndroidModuleImpl(new File(mRoot, mName));
    addModule(mMainModule);
    File codeassist = new File(root, ".idea");
    if (!codeassist.exists()) {
      if (!codeassist.mkdirs()) {}
    }
    mSettings = new ProjectSettings(new File(codeassist, "settings.json"));
  }

  public void clear() {
    mModules.clear();
  }

  public void addModule(Module module) {
    assert module.getProject() == null;
    module.setProject(this);

    mModules.put(module.getName(), module);
  }

  public boolean isCompiling() {
    return mCompiling;
  }

  public void setCompiling(boolean compiling) {
    mCompiling = compiling;
  }

  public void setIndexing(boolean indexing) {
    mIndexing = indexing;
  }

  public boolean isIndexing() {
    return mIndexing;
  }

  public void open() throws IOException {
    mSettings.refresh();
    mMainModule.open();
    ;

    graph.addNode(mMainModule);
    addEdges(graph, mMainModule);
    Set<Module> modules = Graphs.reachableNodes(graph, mMainModule);
    for (Module module : modules) {
      module.open();
      File rootFile = module.getRootFile();
      mModules.put(rootFile.getName(), module);
    }
  }

  public void index() throws IOException {
    Set<Module> modules = Graphs.reachableNodes(graph, mMainModule);
    for (Module module : modules) {
      module.clear();
      module.index();
    }
  }

  /**
   * @return All the modules from the main module, order is not guaranteed
   */
  public Collection<Module> getModules() {
    return mModules.values();
  }

  @NonNull
  public Module getMainModule() {
    return mMainModule;
  }

  public File getRootFile() {
    return mRoot;
  }

  public String getRootName() {
    return mName;
  }

  public ProjectSettings getSettings() {
    return mSettings;
  }

  // public Module getModule(File file) {
  //   return getMainModule();
  // }

  public List<Module> getDependencies(Module module) {
    return ImmutableList.copyOf(mModules.values()).reverse();
  }

  public List<Module> getBuildOrder() throws IOException {
    return getDependencies(mMainModule);
  }

  private void addEdges(MutableGraph<Module> graph, Module module) throws IOException {}

  public Module getModule(File file) {
    for (Module value : mModules.values()) {
      for (ContentRoot contentRoot : value.getContentRoots()) {
        for (File sourceDirectory : contentRoot.getSourceDirectories()) {
          if (directoryContainsFile(sourceDirectory, file)) {
            return value;
          }
        }
      }
    }
    return getMainModule();
  }
  
  public Module getResModule(File file) {
    for (Module value : mModules.values()) {
       if(!(value instanceof AndroidModule))continue;
      for (AndroidContentRoot contentRoot : ((AndroidModule)value).getContentRoots()) {
        for (File sourceDirectory : ((AndroidContentRoot)contentRoot).getResourceDirectories()) {
          if (directoryContainsFile(sourceDirectory, file)) {
            return value;
          }
        }
      }
    }
    return getMainModule();
  }

  public Module getModuleByName(String name) {
    return mModules.get(name);
  }

  private boolean directoryContainsFile(File dir, File file) {
    try {
      File rootFile = dir.getCanonicalFile();
      File absoluteFile = file.getCanonicalFile();

      return absoluteFile.exists()
          && absoluteFile.getAbsolutePath().startsWith(rootFile.getAbsolutePath());
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Project project = (Project) o;
    return mRoot.equals(project.mRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mRoot);
  }

  public EventManager getEventManager() {
    return eventManager;
  }
}
