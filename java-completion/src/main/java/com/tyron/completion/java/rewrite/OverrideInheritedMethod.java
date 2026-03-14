package com.tyron.completion.java.rewrite;

import com.google.common.base.Strings;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.tyron.builder.model.SourceFileObject;
import com.tyron.completion.java.FindNewTypeDeclarationAt;
import com.tyron.completion.java.FindTypeDeclarationAt;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.completion.java.provider.FindHelper;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.java.util.PrintHelper;
import com.tyron.completion.java.util.ProjectUtil;
import com.tyron.completion.model.Position;
import com.tyron.completion.model.Range;
import com.tyron.completion.model.TextEdit;
import dev.mutwakil.javac.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

public class OverrideInheritedMethod implements JavaRewrite2 {

  final String superClassName, methodName;
  final String[] erasedParameterTypes;
  final Path file;
  final int insertPosition;
  private final SourceFileObject sourceFileObject;

  public OverrideInheritedMethod(
      String superClassName,
      String methodName,
      String[] erasedParameterTypes,
      Path file,
      int insertPosition) {
    this.superClassName = superClassName;
    this.methodName = methodName;
    this.erasedParameterTypes = erasedParameterTypes;
    this.file = file;
    this.sourceFileObject = null;
    this.insertPosition = insertPosition;
  }

  public OverrideInheritedMethod(
      String superClassName,
      String methodName,
      String[] erasedParameterTypes,
      SourceFileObject file,
      int insertPosition) {
    this.superClassName = superClassName;
    this.methodName = methodName;
    this.erasedParameterTypes = erasedParameterTypes;
    this.file = null;
    this.sourceFileObject = file;
    this.insertPosition = insertPosition;
  }

  @Override
  public Map<Path, TextEdit[]> rewrite(JavacUtilitiesProvider task) {

    List<TextEdit> edits = new ArrayList<>();
    Position insertPoint = insertNearCursor(task);

    if (insertPoint == Position.NONE) {
      return CANCELLED;
    }

    Types types = task.getTypes();
    Trees trees = task.getTrees();
    ExecutableElement superMethod =
        FindHelper.findMethod2(task, superClassName, methodName, erasedParameterTypes);
    if (superMethod == null) {
      return CANCELLED;
    }

    CompilationUnitTree root = task.root();
    if (root == null) {
      return CANCELLED;
    }

    ClassTree thisTree =
        new FindTypeDeclarationAt(task.getTrees()).scan(root, (long) insertPosition);
    TreePath thisPath = trees.getPath(root, thisTree);

    TypeElement thisClass = (TypeElement) trees.getElement(thisPath);
    ExecutableType parameterizedType =
        (ExecutableType) types.asMemberOf((DeclaredType) thisClass.asType(), superMethod);
    int indent = EditHelper.indent(task, root, thisTree) + 1;

    Set<String> typesToImport = ActionUtil.getTypesToImport(parameterizedType);

    Optional<JavaFileObject> sourceFile = ProjectUtil.getInstance().findAnywhere(superClassName);
    String text;
    if (sourceFile.isPresent()) {
      var unit =
          CompilationInfo.get(ProjectUtil.getInstance().getModule())
              .updateImmediately(sourceFile.get());
      MethodTree source =
          FindHelper.findMethod(
              new DefaultJavacUtilitiesProvider(
                  task.getTask(), unit, ProjectUtil.getInstance().getProject()),
              superClassName,
              methodName,
              erasedParameterTypes);
      if (source == null) {
        text = PrintHelper.printMethod(superMethod, parameterizedType, superMethod);
      } else {
        text = PrintHelper.printMethod(superMethod, parameterizedType, source);
      }

    } else {
      text = PrintHelper.printMethod(superMethod, parameterizedType, superMethod);
    }

    String tabs = Strings.repeat("\t", indent);
    text = tabs + text.replace("\n", "\n" + tabs) + "\n\n";

    edits.add(new TextEdit(new Range(insertPoint, insertPoint), text));

    File source =
        file != null ? file.toFile() : Objects.requireNonNull(sourceFileObject).mFile.toFile();
    for (String s : typesToImport) {
      if (!ActionUtil.hasImport(root, s)) {
        JavaRewrite2 addImport = new AddImport(source, s);
        Map<Path, TextEdit[]> rewrite = addImport.rewrite(task);
        TextEdit[] textEdits = rewrite.get(source.toPath());
        if (textEdits != null) {
          Collections.addAll(edits, textEdits);
        }
      }
    }
    return Collections.singletonMap(source.toPath(), edits.toArray(new TextEdit[0]));
  }

  private Position insertNearCursor(JavacUtilitiesProvider task) {

    ClassTree parent =
        new FindTypeDeclarationAt(task.getTrees()).scan(task.root(), (long) insertPosition);
    if (parent == null) {
      parent =
          new FindNewTypeDeclarationAt(task.getTrees(), task.root())
              .scan(task.root(), (long) insertPosition);
    }
    Position next = nextMember(task, parent);
    if (next != Position.NONE) {
      return next;
    }
    return EditHelper.insertAtEndOfClass(task, task.root(), parent);
  }

  private Position nextMember(JavacUtilitiesProvider task, ClassTree parent) {
    SourcePositions pos = task.getTrees().getSourcePositions();
    if (parent != null) {
      for (Tree member : parent.getMembers()) {
        long start = pos.getStartPosition(task.root(), member);
        if (start > insertPosition) {
          int line = (int) task.root().getLineMap().getLineNumber(start);
          return new Position(line - 1, 0);
        }
      }
    }
    return Position.NONE;
  }
}
