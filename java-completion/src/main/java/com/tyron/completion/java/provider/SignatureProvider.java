package com.tyron.completion.java.provider;

import androidx.annotation.NonNull;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import com.tyron.builder.project.api.Module;
import com.tyron.common.progress.ICancelChecker;
import com.tyron.completion.java.hover.ShortTypePrinter;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.util.MarkdownHelper;
import com.tyron.completion.java.util.ProjectUtil;
import com.tyron.completion.java.visitors.FindInvocationAt;
import com.tyron.completion.model.signatures.ParameterInformation;
import com.tyron.completion.model.signatures.SignatureHelp;
import com.tyron.completion.model.signatures.SignatureHelpParams;
import com.tyron.completion.model.signatures.SignatureInformation;
import dev.mutwakil.javac.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public class SignatureProvider extends CancelableServiceProvider {

  public static final SignatureHelp NOT_SUPPORTED =
      new SignatureHelp(Collections.emptyList(), -1, -1);
  private final CompilationInfo compilationInfo;

  public SignatureProvider(Module module, ICancelChecker cancelChecker) {
    super(cancelChecker);
    this.compilationInfo = CompilationInfo.get(module);
  }

  @NonNull
  public SignatureHelp signatureHelp(@NonNull SignatureHelpParams params) {
    return signatureHelp(
        params.getFile(), params.getPosition().getLine(), params.getPosition().getColumn());
  }

  @NonNull
  public SignatureHelp signatureHelp(Path file, int l, int c) {

    // 1-based line and column index
    final int line = l + 1;
    final int column = c + 1;

    // TODO prune
    abortIfCancelled();

    if (compilationInfo == null) return NOT_SUPPORTED;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toFile().toURI());
    if (unit == null) return NOT_SUPPORTED;
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    long cursor = unit.getLineMap().getPosition(line, column);
    TreePath path = new FindInvocationAt(javacTask, this).scan(unit, cursor);
    if (path == null) {
      return NOT_SUPPORTED;
    }
    var task = new DefaultJavacUtilitiesProvider(javacTask, unit, null);

    if (path.getLeaf() instanceof MethodInvocationTree) {
      MethodInvocationTree invoke = (MethodInvocationTree) path.getLeaf();
      List<ExecutableElement> overloads = methodOverloads(task, invoke);
      List<SignatureInformation> signatures = new ArrayList<>();
      for (ExecutableElement method : overloads) {
        SignatureInformation info = info(method);
        addSourceInfo(task, method, info);
        addFancyLabel(info);
        signatures.add(info);
      }
      int activeSignature = activeSignature(task, path, invoke.getArguments(), overloads);
      int activeParameter = activeParameter(task, invoke.getArguments(), cursor);
      return new SignatureHelp(signatures, activeSignature, activeParameter);
    }
    if (path.getLeaf() instanceof NewClassTree) {
      NewClassTree invoke = (NewClassTree) path.getLeaf();
      List<ExecutableElement> overloads = constructorOverloads(task, invoke);
      List<SignatureInformation> signatures = new ArrayList<>();
      for (ExecutableElement method : overloads) {
        SignatureInformation info = info(method);
        addSourceInfo(task, method, info);
        addFancyLabel(info);
        signatures.add(info);
      }
      int activeSignature = activeSignature(task, path, invoke.getArguments(), overloads);
      int activeParameter = activeParameter(task, invoke.getArguments(), cursor);
      return new SignatureHelp(signatures, activeSignature, activeParameter);
    }
    return NOT_SUPPORTED;
  }

  private List<ExecutableElement> methodOverloads(
      JavacUtilitiesProvider task, @NonNull MethodInvocationTree method) {
    abortIfCancelled();
    if (method.getMethodSelect() instanceof IdentifierTree) {
      IdentifierTree id = (IdentifierTree) method.getMethodSelect();
      return scopeOverloads(task, id);
    }
    if (method.getMethodSelect() instanceof MemberSelectTree) {
      MemberSelectTree select = (MemberSelectTree) method.getMethodSelect();
      return memberOverloads(task, select);
    }
    throw new RuntimeException(method.getMethodSelect().toString());
  }

  @NonNull
  private List<ExecutableElement> scopeOverloads(
      @NonNull JavacUtilitiesProvider task, IdentifierTree method) {
    abortIfCancelled();
    Trees trees = task.getTrees();
    TreePath path = trees.getPath(task.root(), method);
    Scope scope = trees.getScope(path);
    List<ExecutableElement> list = new ArrayList<>();
    Predicate<CharSequence> filter = name -> method.getName().contentEquals(name);
    // TODO add static imports
    for (Element member : ScopeHelper.scopeMembers(task, scope, filter)) {
      if (member.getKind() == ElementKind.METHOD) {
        list.add((ExecutableElement) member);
      }
    }
    return list;
  }

  @NonNull
  private List<ExecutableElement> memberOverloads(
      @NonNull JavacUtilitiesProvider task, @NonNull MemberSelectTree method) {
    abortIfCancelled();
    Trees trees = task.getTrees();
    TreePath path = trees.getPath(task.root(), method.getExpression());
    boolean isStatic = trees.getElement(path) instanceof TypeElement;
    Scope scope = trees.getScope(path);
    TypeElement type = typeElement(trees.getTypeMirror(path));

    if (type == null) {
      return Collections.emptyList();
    }

    List<ExecutableElement> list = new ArrayList<>();
    for (Element member : task.getTask().getElements().getAllMembers(type)) {
      if (member.getKind() != ElementKind.METHOD) {
        continue;
      }
      if (!member.getSimpleName().contentEquals(method.getIdentifier())) {
        continue;
      }
      if (isStatic != member.getModifiers().contains(Modifier.STATIC)) {
        continue;
      }
      if (!trees.isAccessible(scope, member, (DeclaredType) type.asType())) {
        continue;
      }
      list.add((ExecutableElement) member);
    }
    return list;
  }

  private TypeElement typeElement(TypeMirror type) {
    abortIfCancelled();
    if (type instanceof DeclaredType) {
      DeclaredType declared = (DeclaredType) type;
      return (TypeElement) declared.asElement();
    }
    if (type instanceof TypeVariable) {
      TypeVariable variable = (TypeVariable) type;
      return typeElement(variable.getUpperBound());
    }
    return null;
  }

  @NonNull
  private List<ExecutableElement> constructorOverloads(
      @NonNull JavacUtilitiesProvider task, @NonNull NewClassTree method) {
    abortIfCancelled();
    Trees trees = task.getTrees();
    TreePath path = trees.getPath(task.root(), method.getIdentifier());
    Scope scope = trees.getScope(path);
    TypeElement type = (TypeElement) trees.getElement(path);
    List<ExecutableElement> list = new ArrayList<>();
    for (Element member : task.getTask().getElements().getAllMembers(type)) {
      if (member.getKind() != ElementKind.CONSTRUCTOR) {
        continue;
      }
      if (!trees.isAccessible(scope, member, (DeclaredType) type.asType())) {
        continue;
      }
      list.add((ExecutableElement) member);
    }
    return list;
  }

  @NonNull
  private SignatureInformation info(@NonNull ExecutableElement method) {
    abortIfCancelled();
    SignatureInformation info = new SignatureInformation();
    info.setLabel(method.getSimpleName().toString());
    if (method.getKind() == ElementKind.CONSTRUCTOR) {
      info.setLabel(method.getEnclosingElement().getSimpleName().toString());
    }
    info.setParameters(parameters(method));
    return info;
  }

  @NonNull
  private List<ParameterInformation> parameters(@NonNull ExecutableElement method) {
    abortIfCancelled();
    List<ParameterInformation> list = new ArrayList<>();
    for (VariableElement p : method.getParameters()) {
      list.add(parameter(p));
    }
    return list;
  }

  @NonNull
  private ParameterInformation parameter(@NonNull VariableElement p) {
    abortIfCancelled();
    ParameterInformation info = new ParameterInformation();
    info.setLabel(ShortTypePrinter.NO_PACKAGE.print(p.asType()));
    return info;
  }

  private void addSourceInfo(
      @NonNull JavacUtilitiesProvider task,
      @NonNull ExecutableElement method,
      @NonNull SignatureInformation info) {
    abortIfCancelled();
    final var type = (TypeElement) method.getEnclosingElement();
    final var className = type.getQualifiedName().toString();
    final var methodName = method.getSimpleName().toString();
    final var erasedParameterTypes = FindHelper.erasedParameterTypes(task, method);
    final var file = ProjectUtil.getInstance().findAnywhere(className);

    if (!file.isPresent()) {
      return;
    }
    final var unit = compilationInfo.updateImmediately(file.get());
    final var source =
        FindHelper.findMethod(
            new DefaultJavacUtilitiesProvider(task.getTask(), unit, null),
            className,
            methodName,
            erasedParameterTypes);
    if (source == null) {
      return;
    }

    final var path = task.getTrees().getPath(unit, source);
    final var docTree = MDocTrees.instance(task.getTask()).getDocCommentTree(path);

    if (docTree != null) {
      info.setDocumentation(MarkdownHelper.asMarkupContent(docTree));
    }

    info.setParameters(parametersFromSource(source));
  }

  private void addFancyLabel(@NonNull SignatureInformation info) {
    abortIfCancelled();
    StringJoiner join = new StringJoiner(", ");
    for (ParameterInformation p : info.getParameters()) {
      join.add(p.getLabel());
    }
    info.setLabel(info.getLabel() + "(" + join + ")");
  }

  @NonNull
  private List<ParameterInformation> parametersFromSource(MethodTree source) {
    abortIfCancelled();
    List<ParameterInformation> list = new ArrayList<>();
    for (VariableTree p : source.getParameters()) {
      ParameterInformation info = new ParameterInformation();
      info.setLabel(p.getType() + " " + p.getName());
      list.add(info);
    }
    return list;
  }

  private int activeParameter(
      @NonNull JavacUtilitiesProvider task,
      @NonNull List<? extends ExpressionTree> arguments,
      long cursor) {
    abortIfCancelled();
    SourcePositions pos = task.getTrees().getSourcePositions();
    CompilationUnitTree root = task.root();
    for (int i = 0; i < arguments.size(); i++) {
      long end = pos.getEndPosition(root, arguments.get(i));
      if (cursor <= end) {
        return i;
      }
    }
    return arguments.size();
  }

  private int activeSignature(
      JavacUtilitiesProvider task,
      TreePath invocation,
      List<? extends ExpressionTree> arguments,
      List<ExecutableElement> overloads) {
    abortIfCancelled();
    for (int i = 0; i < overloads.size(); i++) {
      if (isCompatible(task, invocation, arguments, overloads.get(i))) {
        return i;
      }
    }
    return 0;
  }

  private boolean isCompatible(
      JavacUtilitiesProvider task,
      TreePath invocation,
      List<? extends ExpressionTree> arguments,
      ExecutableElement overload) {
    abortIfCancelled();
    if (arguments.size() > overload.getParameters().size()) {
      return false;
    }
    for (int i = 0; i < arguments.size(); i++) {
      ExpressionTree argument = arguments.get(i);
      TypeMirror argumentType = task.getTrees().getTypeMirror(new TreePath(invocation, argument));
      TypeMirror parameterType = overload.getParameters().get(i).asType();
      if (!isCompatible(task, argumentType, parameterType)) {
        return false;
      }
    }
    return true;
  }

  private boolean isCompatible(
      JavacUtilitiesProvider task, TypeMirror argument, TypeMirror parameter) {
    abortIfCancelled();
    if (argument instanceof ErrorType) {
      return true;
    }
    if (argument instanceof PrimitiveType) {
      argument = task.getTask().getTypes().boxedClass((PrimitiveType) argument).asType();
    }
    if (parameter instanceof PrimitiveType) {
      parameter = task.getTask().getTypes().boxedClass((PrimitiveType) parameter).asType();
    }
    if (argument instanceof ArrayType) {
      if (!(parameter instanceof ArrayType)) {
        return false;
      }
      ArrayType argumentA = (ArrayType) argument;
      ArrayType parameterA = (ArrayType) parameter;
      return isCompatible(task, argumentA.getComponentType(), parameterA.getComponentType());
    }
    if (argument instanceof DeclaredType) {
      if (!(parameter instanceof DeclaredType)) {
        return false;
      }
      argument = task.getTask().getTypes().erasure(argument);
      parameter = task.getTask().getTypes().erasure(parameter);
      return argument.toString().equals(parameter.toString());
    }
    return true;
  }
}
