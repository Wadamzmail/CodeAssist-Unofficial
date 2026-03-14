package com.tyron.completion.java.provider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.util.Context;
import com.tyron.builder.project.Project;
import com.tyron.completion.CompletionParameters;
import dev.mutwakil.javac.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class DefaultJavacUtilitiesProvider implements JavacUtilitiesProvider {

  private final JavacTaskImpl task;
  private final CompilationUnitTree root;
  private final Project project;
  private CompletionParameters parameters;

  public DefaultJavacUtilitiesProvider(
      JavacTaskImpl task, CompilationUnitTree root, Project project) {
    this(task, root, project, null);
  }

  public DefaultJavacUtilitiesProvider(
      JavacTaskImpl task,
      CompilationUnitTree root,
      Project project,
      CompletionParameters parameters) {
    this.task = task;
    this.root = root;
    this.project = project;
    this.parameters = parameters;
  }

  @Override
  public JavacTaskImpl getTask() {
    return task;
  }

  @Override
  public Context getContext() {
    return task.getContext();
  }

  @Override
  public Trees getTrees() {
    return MTrees.instance(task);
  }

  @Override
  public Elements getElements() {
    return task.getElements();
  }

  @Override
  public Types getTypes() {
    return task.getTypes();
  }

  @Override
  public CompilationUnitTree root() {
    return root;
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public CompletionParameters getParameters() {
    return parameters;
  }
}
