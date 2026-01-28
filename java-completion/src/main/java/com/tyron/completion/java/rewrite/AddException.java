package com.tyron.completion.java.rewrite;

import com.google.common.collect.ImmutableMap;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import com.tyron.builder.project.api.Module;
import com.tyron.completion.java.ShortNamesCache;
import com.tyron.completion.java.provider.FindHelper;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.java.util.ProjectUtil;
import com.tyron.completion.model.Range;
import com.tyron.completion.model.TextEdit;
import dev.mutwakil.javac.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;

public class AddException implements JavaRewrite2 {

  private final String className;
  private final String methodName;
  private final String[] erasedParameterTypes;
  private String exceptionType;

  public AddException(
      String className, String methodName, String[] erasedParameterTypes, String exceptionType) {
    this.className = className;
    this.methodName = methodName;
    this.erasedParameterTypes = erasedParameterTypes;
    this.exceptionType = exceptionType;
  }

  @Override
  public Map<Path, TextEdit[]> rewrite(JavacUtilitiesProvider task) {

    List<TextEdit> edits = new ArrayList<>();

    Path file = ProjectUtil.getInstance().findTypeDeclaration(className);
    if (file == ProjectUtil.NOT_FOUND) {
      return CANCELLED;
    }
    CompilationUnitTree root = task.root();
    if (root == null) {
      return CANCELLED;
    }
    // for import
    final Module module = task.getProject().getModule(file.toFile());
    ShortNamesCache cache = ShortNamesCache.getInstance(module);

    Trees trees = task.getTrees();
    ExecutableElement methodElement =
        FindHelper.findMethod2(task, className, methodName, erasedParameterTypes);
    MethodTree methodTree = trees.getTree(methodElement);
    SourcePositions pos = trees.getSourcePositions();
    long startBody = pos.getStartPosition(root, methodTree.getBody());
    String packageName = "";
    String simpleName = exceptionType;
    int lastDot = simpleName.lastIndexOf('.');
    if (lastDot != -1) {
      packageName = exceptionType.substring(0, lastDot);
      simpleName = exceptionType.substring(lastDot + 1);
    }
    String insertText;
    if (methodTree.getThrows().isEmpty()) {
      insertText = " throws " + simpleName + " ";
    } else {
      insertText = ", " + simpleName + " ";
    }
    String qualifiedName = packageName + simpleName;
    if (!qualifiedName.contains(".")) {
      for (String it : cache.getAllClassNames()) {
        if (it.endsWith("." + simpleName)) {
          //        if(it.startsWith("java.lang."))break;
          qualifiedName = it;
          break;
        }
      }
    }
    if (!ActionUtil.hasImport(root, qualifiedName)) {
      AddImport addImport = new AddImport(file.toFile(), qualifiedName);
      Map<Path, TextEdit[]> rewrite = addImport.rewrite(task);
      TextEdit[] imports = rewrite.get(file);
      if (imports != null) {
        Collections.addAll(edits, imports);
      }
    }

    TextEdit insertThrows = new TextEdit(new Range(startBody - 1, startBody - 1), insertText);
    edits.add(insertThrows);
    return ImmutableMap.of(file, edits.toArray(new TextEdit[0]));
  }
}
