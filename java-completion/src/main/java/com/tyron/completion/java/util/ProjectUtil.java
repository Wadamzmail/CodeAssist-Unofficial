package com.tyron.completion.java.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;

/*
* @author Wadamzmail
*/
public class ProjectUtil {
 
 private Project mProject;
 private JavaModule mCurrentModule;
 private static ProjectUtil instance;
 
 public static ProjectUtil getInstance(){
  if(instance == null) {
      instance = new ProjectUtil();
  }
  return instance;
 }
 
 private ProjectUtil(){}
 
 public ProjectUtil setProject(Project project){
  this.mProject = project; 
  return instance;
 }
 
 public ProjectUtil setModule(Module module){

  if (module instanceof JavaModule) {
      mCurrentModule = (JavaModule) module;
  } else {
      throw new IllegalArgumentException("Module must be a JavaModule");
  }
  return instance;
 }
 
 public Project getProject(){
     return mProject;
 }
 
 public JavaModule getModule(){
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
}
