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
  private static final JavaModule JDK_MODULE = new JavaModuleImpl(null);

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

    Deque<Module> queue = new LinkedList<>();
    Set<Module> visitedModules = new HashSet<>();
//    queue.addLast(module);

    //    while (!queue.isEmpty()) {
    //      Module current = queue.removeFirst();
    //
    //      if (current instanceof JavaModule) {
    //        JavaModule javaModule = (JavaModule) current;
    //        classNames.addAll(javaModule.getClassIndex().getLeafNodes());
    //      }
    //
    //      visitedModules.add(current);
    //      for (String path : current.getModuleDependencies()) {
    //        Module dependingModule = current.getProject().getModuleByName(path);
    //        if (dependingModule instanceof JavaModule) {
    //          JavaModule javaModule = (JavaModule) current;
    //          classNames.addAll(javaModule.getApiClassIndex().getLeafNodes());
    //          indexApiModules(javaModule, classNames, queue, visitedModules);
    //        }
    //        if (dependingModule != null && !visitedModules.contains(dependingModule)) {
    //          queue.addLast(dependingModule);
    //        }
    //      }
    //    }
    JavaModule jvModule = (JavaModule)module;
    classNames.addAll(jvModule.getClassIndex().getLeafNodes());  
    for (String depName : jvModule.getAllProjects()) {
        Module dep = jvModule.getProject().getModuleByName(depName);
        if (dep == null) {
          continue;
        }   
        getApiClassNames(jvModule, classNames, visitedModules);
    }   

    classNames.addAll(JDK_MODULE.getClassIndex().getLeafNodes());
    return classNames.toArray(new String[0]);
  }

  private static void getApiClassNames(
      JavaModule depModule,
      Set<String> classNames,
      Set<Module> visitedModules) {
      
    for (String apiName : depModule.getApiProjects()) {
      Module m = depModule.getProject().getModuleByName(apiName);

      JavaModule apiModule = (JavaModule) m;

      if (visitedModules.contains(apiModule)) {
        continue;
      }
     visitedModules.add(apiModule); 

      classNames.addAll(apiModule.getApiClassIndex().getLeafNodes());
      getApiClassNames(apiModule, classNames,visitedModules);
      
    } 
  }
  
}
