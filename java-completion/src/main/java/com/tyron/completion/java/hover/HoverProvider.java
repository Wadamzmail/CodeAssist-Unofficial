package com.tyron.completion.java.hover;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.tyron.completion.java.provider.FindHelper;
import dev.mutwakil.javac.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.sun.source.util.DocTrees;
import com.tyron.completion.java.util.ProjectUtil;

public class HoverProvider {

  final JavacUtilitiesProvider task;

  public static final List<String> NOT_SUPPORTED = Collections.emptyList();

  public HoverProvider(JavacUtilitiesProvider provider) {
    task = provider;
  }

  public List<String> hover(Path file, int offset) {
          Element element = new FindHoverElement(task).scan(task.root(), (long) offset);
          if (element == null) {
            return NOT_SUPPORTED;
          }
          List<String> list = new ArrayList<>();
          String code = printType(element);
          list.add(code);
          String docs = docs(task, element);
          if (!docs.isEmpty()) {
            list.add(docs);
          }
          return list;
 
  }

  public String docs(JavacUtilitiesProvider task, Element element) {
    if (element instanceof TypeElement) {
      TypeElement type = (TypeElement) element;
      String className = type.getQualifiedName().toString();
     // TODO
     // NOTE:
     // Source lookup outside current CompilationUnit requires JavaCompilerService
     // and SOURCE_PATH (Docs). Not available via JavacUtilitiesProvider.
      Optional<JavaFileObject> file = ProjectUtil.getInstance().findAnywhere(className);
       if (!file.isPresent()) return "";
     return "";
      Tree tree = FindHelper.findType(task, className);
      return docs(task, tree);
    } else if (element.getKind() == ElementKind.FIELD) {
      VariableElement field = (VariableElement) element;
      TypeElement type = (TypeElement) field.getEnclosingElement();
      String className = type.getQualifiedName().toString();
      Optional<JavaFileObject> file = ProjectUtil.getInstance().findAnywhere(className);
      if (!file.isPresent()) return "";
      Tree tree = FindHelper.findType(task, className);
      return docs(task, tree);
    } else if (element instanceof ExecutableElement) {
      ExecutableElement method = (ExecutableElement) element;
      TypeElement type = (TypeElement) method.getEnclosingElement();
      String className = type.getQualifiedName().toString();
      String methodName = method.getSimpleName().toString();
      String[] erasedParameterTypes = FindHelper.erasedParameterTypes(task, method);
      Optional<JavaFileObject> file = ProjectUtil.getInstance().findAnywhere(className);
      if (!file.isPresent()) return "";
      Tree tree = FindHelper.findMethod(task, className, methodName, erasedParameterTypes);
      return docs(task, tree);
    } else {
      return "";
    }
  }

  private String docs(JavacUtilitiesProvider task, Tree tree) {
    TreePath path = task.getTrees().getPath(task.root(), tree);
    DocCommentTree docTree = ((DocTrees) task.getTrees()).getDocCommentTree(path);
    if (docTree == null) return "";
    // TODO: format this
    return docTree.toString();
  }

  private String printType(Element e) {
    if (e instanceof ExecutableElement) {
      ExecutableElement m = (ExecutableElement) e;
      return ShortTypePrinter.DEFAULT.printMethod(m);
    } else if (e instanceof VariableElement) {
      VariableElement v = (VariableElement) e;
      return ShortTypePrinter.DEFAULT.print(v.asType()) + " " + v;
    } else if (e instanceof TypeElement) {
      TypeElement t = (TypeElement) e;
      StringJoiner lines = new StringJoiner("\n");
      lines.add(hoverTypeDeclaration(t) + " {");
      for (Element member : t.getEnclosedElements()) {
        // TODO check accessibility
        if (member instanceof ExecutableElement || member instanceof VariableElement) {
          lines.add("  " + printType(member) + ";");
        } else if (member instanceof TypeElement) {
          lines.add("  " + hoverTypeDeclaration((TypeElement) member) + " { /* removed " + "*/ }");
        }
      }
      lines.add("}");
      return lines.toString();
    } else {
      return e.toString();
    }
  }

  private String hoverTypeDeclaration(TypeElement t) {
    StringBuilder result = new StringBuilder();
    switch (t.getKind()) {
      case ANNOTATION_TYPE:
        result.append("@interface");
        break;
      case INTERFACE:
        result.append("interface");
        break;
      case CLASS:
        result.append("class");
        break;
      case ENUM:
        result.append("enum");
        break;
      default:
        result.append("_");
    }
    result.append(" ").append(ShortTypePrinter.DEFAULT.print(t.asType()));
    String superType = ShortTypePrinter.DEFAULT.print(t.getSuperclass());
    switch (superType) {
      case "Object":
      case "none":
        break;
      default:
        result.append(" extends ").append(superType);
    }
    return result.toString();
  }
}
