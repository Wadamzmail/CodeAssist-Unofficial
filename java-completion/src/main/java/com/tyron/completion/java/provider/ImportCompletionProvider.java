package com.tyron.completion.java.provider;

import static com.tyron.completion.java.provider.MemberSelectCompletionProvider.putMethod;
import static com.tyron.completion.java.util.CompletionItemFactory.importClassItem;
import static com.tyron.completion.java.util.CompletionItemFactory.item;
import static com.tyron.completion.java.util.CompletionItemFactory.method;
import static com.tyron.completion.java.util.CompletionItemFactory.packageItem;
import static com.tyron.completion.progress.ProgressManager.checkCanceled;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.tyron.common.util.StringSearch;
import com.tyron.completion.java.compiler.CompileTask;
import com.tyron.completion.java.compiler.JavaCompilerService;
import com.tyron.completion.model.CompletionItem;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.model.DrawableKind;
import dev.mutwakil.javac.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import com.tyron.completion.java.util.ProjectUtil;
import com.tyron.completion.java.ShortNamesCache;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.util.PackageTrie;

public class ImportCompletionProvider extends BaseCompletionProvider {

  public ImportCompletionProvider(JavaCompilerService service) {
    super(service);
  }

@Override
public void complete(
    CompletionList.Builder builder,
    JavacUtilitiesProvider task,
    TreePath treePath,
    String path,
    boolean endsWithParen) {

  checkCanceled();

  CompilationUnitTree root = task.root();
  File file = new File(root.getSourceFile().toUri());

  Module module = task.getProject().getModule(file);
  if (!(module instanceof JavaModule)) return;

  JavaModule javaModule = (JavaModule) module;
  PackageTrie trie = javaModule.getClassIndex();

  boolean endsWithDot = path.endsWith(".");
  String query = endsWithDot ? path.substring(0, path.length() - 1) : path;

  int lastDot = query.lastIndexOf('.');
  String parentPackage = lastDot == -1 ? "" : query.substring(0, lastDot);
  String prefix = lastDot == -1 ? query : query.substring(lastDot + 1);

  Set<String> added = new HashSet<>();

  if (parentPackage.isEmpty() && !endsWithDot) {
    for (String top : trie.getTopLevelNonLeafNodes()) {
      if (!top.startsWith(prefix)) continue;
      if (!added.add(top)) continue;

      CompletionItem item = packageItem(top);
      item.addFilterText(top);
      builder.addItem(item);
    }
    return;
  }

  String basePackage = endsWithDot ? query : parentPackage;

  List<String> matches = trie.getMatchingPackages(basePackage);

  for (String fqn : matches) {
    checkCanceled();

    if (!fqn.startsWith(basePackage)) continue;

    String remaining =
        fqn.length() == basePackage.length()
            ? ""
            : fqn.substring(basePackage.length() + 1);

    if (remaining.isEmpty()) continue;

    int dot = remaining.indexOf('.');
    String segment = dot == -1 ? remaining : remaining.substring(0, dot);

    if (!segment.startsWith(prefix)) continue;
    if (!added.add(segment)) continue;

    CompletionItem item;
    if (dot == -1) {
      item = importClassItem(fqn);
    } else {
      item = packageItem(segment);
    }

    item.addFilterText(segment);
    builder.addItem(item);

    if (builder.getItemCount() >= Completions.MAX_COMPLETION_ITEMS) {
      builder.incomplete();
      return;
    }
  }
}

  public List<CompletionItem> addAnonymous(JavacUtilitiesProvider task, TreePath path, String partial) {
    checkCanceled();

    List<CompletionItem> items = new ArrayList<>();

    if (!(path.getLeaf() instanceof NewClassTree)) {
      return items;
    }

    if (path.getParentPath().getParentPath().getLeaf().getKind() == Tree.Kind.METHOD_INVOCATION) {
      Trees trees = MTrees.instance(task.getTask());
      MethodInvocationTree method =
          (MethodInvocationTree) path.getParentPath().getParentPath().getLeaf();
      Element element = trees.getElement(path.getParentPath().getParentPath());

      if (element instanceof ExecutableElement) {
        ExecutableElement executable = (ExecutableElement) element;

        int argumentToComplete = 0;
        for (int i = 0; i < method.getArguments().size(); i++) {
          ExpressionTree exp = method.getArguments().get(i);
          if (exp.toString().equals(partial) || exp.toString().equals("new " + partial)) {
            argumentToComplete = i;
          }
        }

        VariableElement var = executable.getParameters().get(argumentToComplete);
        if (var.asType() instanceof DeclaredType) {
          DeclaredType type = (DeclaredType) var.asType();
          Element classElement = type.asElement();

          if (StringSearch.matchesPartialName(classElement.getSimpleName().toString(), partial)) {
            CompletionItem item = new CompletionItem();

            StringBuilder sb = new StringBuilder();
            if (classElement instanceof TypeElement) {
              TypeElement typeElement = (TypeElement) classElement;
              // import the class
              item.action = CompletionItem.Kind.IMPORT;
              item.data = typeElement.getQualifiedName().toString();
              item.iconKind = DrawableKind.Interface;
              item.label = classElement.getSimpleName().toString() + " {...}";
              item.commitText =
                  "" + classElement.getSimpleName() + "() {\n" + "\t" + "// TODO\n" + "}";
              item.cursorOffset = item.commitText.length();
              item.detail = "";
            }
            items.add(item);
          }
        }
      }
    }
    return items;
  }

  public static void addStaticImports(
      JavacUtilitiesProvider task,
      CompilationUnitTree root,
      String partial,
      boolean endsWithParen,
      CompletionList.Builder list) {
    checkCanceled();

    Trees trees = MTrees.instance(task.getTask());
    HashMap<String, List<ExecutableElement>> methods = new HashMap<>();
    for (ImportTree i : root.getImports()) {
      if (!i.isStatic()) continue;
      MemberSelectTree id = (MemberSelectTree) i.getQualifiedIdentifier();
      if (!importMatchesPartial(id.getIdentifier(), partial)) continue;
      TreePath path = trees.getPath(root, id.getExpression());
      TypeElement type = (TypeElement) trees.getElement(path);
      for (Element member : type.getEnclosedElements()) {
        if (!member.getModifiers().contains(Modifier.STATIC)) continue;
        if (!memberMatchesImport(id.getIdentifier(), member)) continue;
        if (FuzzySearch.partialRatio(String.valueOf(member.getSimpleName()), partial) < 70)
          continue;
        if (member.getKind() == ElementKind.METHOD) {
          methods.clear();
          putMethod((ExecutableElement) member, methods);
          for (List<ExecutableElement> overloads : methods.values()) {
            for (CompletionItem item :
                method(task, overloads, endsWithParen, false, (DeclaredType) type.asType())) {
              item.setSortText(JavaSortCategory.ACCESSIBLE_SYMBOL.toString());
              list.addItem(item);
            }
          }
        } else {
          CompletionItem item = item(member);
          item.setSortText(JavaSortCategory.ACCESSIBLE_SYMBOL.toString());
          list.addItem(item);
        }
      }
    }
  }

  private static boolean importMatchesPartial(Name staticImport, String partial) {
    return staticImport.contentEquals("*")
        || StringSearch.matchesPartialName(staticImport, partial);
  }

  private static boolean memberMatchesImport(Name staticImport, Element member) {
    return staticImport.contentEquals("*") || staticImport.contentEquals(member.getSimpleName());
  }
}
