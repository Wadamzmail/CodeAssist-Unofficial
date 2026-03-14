package com.tyron.completion.java;

import com.tyron.builder.model.CodeAssistLibrary;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.impl.JavaModuleImpl;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/** Allows to retrieve java classes in a project by non-qualified names */
public class ShortNamesCache {

  private static final Map<Module, ShortNamesCache> map = new WeakHashMap<>();

  public static ShortNamesCache getInstance(Module module) {
    ShortNamesCache cache = map.get(module);
    if (cache == null) {
      cache = new ShortNamesCache(module);
      map.put(module, cache);
    }
    return cache;
  }

  /** module used to store JDK indexes */
  public static final JavaModule JDK_MODULE = new JavaModuleImpl(null);

  static {
    JDK_MODULE.addLibrary(
        CodeAssistLibrary.forJar(Objects.requireNonNull(CompletionModule.getAndroidJar())));
  }

  private final Module module;

  public ShortNamesCache(Module module) {
    this.module = module;
  }

  /**
   * Returns the list of fully qualified names of all classes in the project and (optionally)
   * libraries.
   */
  public String[] getAllClassNames() {
    if (!(module instanceof JavaModule)) {
      return new String[0];
    }

    Set<String> classNames = new HashSet<>();
    Set<Module> visitedModules = new HashSet<>();
    Set<Module> pending = new HashSet<>();

    if (module instanceof JavaModule) {
      JavaModule javaModule = (JavaModule) module;
      classNames.addAll(javaModule.getClassIndex().getLeafNodes());
    }

    visitedModules.add(module);
    for (String name : module.getModuleDependencies()) {
      Module dependingModule = module.getProject().getModuleByName(name);
      if (dependingModule != null && !visitedModules.contains(dependingModule)) {
        // queue.addLast(dependingModule);
        if (dependingModule instanceof JavaModule) {
          classNames.addAll(((JavaModule) dependingModule).getApiClassIndex().getLeafNodes());
          // pending.add(dependingModule);
          System.out.println("Module Dependency :" + name + " from :" + module.getModuleName());
          for (String name2 : ((JavaModule) dependingModule).getApiProjects()) {
            Module dm1 = dependingModule.getProject().getModuleByName(name2);
            pending.add(dm1);
            System.out.println(
                "added Module Dependency :" + name + " from :" + module.getModuleName());
          }
        }
      }
    }

    getAllApiClassNames(visitedModules, classNames, pending);

    classNames.addAll(JDK_MODULE.getClassIndex().getLeafNodes());
    return classNames.toArray(new String[0]);
  }

  private void getAllApiClassNames(
      Set<Module> visitedModules, Set<String> classNames, Set<Module> pending) {

    Deque<Module> queue = new LinkedList<>();
    for (Module m : pending) {
      if (!(m instanceof JavaModule)) {
        continue;
      }
      queue.addLast(m);
    }
    while (!queue.isEmpty()) {
      Module current = queue.removeFirst();
      if (current instanceof JavaModule) {
        JavaModule javaModule = (JavaModule) current;
        classNames.addAll(javaModule.getApiClassIndex().getLeafNodes());
        System.out.println("Api Module indexed :" + current.getModuleName());
      }

      visitedModules.add(current);
      for (String name : current.getApiProjects()) {
        Module dependingModule = current.getProject().getModuleByName(name);
        System.out.println("Api Module Dependency :" + name + " from :" + current.getModuleName());
        if (dependingModule != null && !visitedModules.contains(dependingModule)) {
          queue.addLast(dependingModule);
          System.out.println(
              "Added Api Module Dependency :" + name + " from :" + current.getModuleName());
        }
      }
    }
  }
}
