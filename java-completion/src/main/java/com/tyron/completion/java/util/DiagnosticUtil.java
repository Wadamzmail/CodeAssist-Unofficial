package com.tyron.completion.java.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.completion.java.action.FindMethodDeclarationAt;
import com.tyron.completion.java.compiler.CompileTask;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.editor.CharPosition;
import com.tyron.editor.Editor;
import dev.mutwakil.javac.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public class DiagnosticUtil {

  private static final Pattern UNREPORTED_EXCEPTION =
      Pattern.compile("unreported exception (" + "(\\w+\\.)*\\w+)");

  public static void setLineAndColumn(DiagnosticWrapper diagnostic, Editor editor) {
    try {
      if (diagnostic.getStartLine() <= -1 && diagnostic.getStartPosition() > 0) {
        CharPosition start = editor.getCharPosition(((int) diagnostic.getStartPosition()));
        diagnostic.setStartLine(start.getLine() + 1);
        diagnostic.setStartColumn(start.getColumn());
        diagnostic.setLineNumber(start.getLine() + 1);
        diagnostic.setColumnNumber(start.getColumn());
      }
      if (diagnostic.getEndLine() <= -1 && diagnostic.getEndPosition() > 0) {
        CharPosition end = editor.getCharPosition(((int) diagnostic.getEndPosition()));
        diagnostic.setEndLine(end.getLine() + 1);
        diagnostic.setEndColumn(end.getColumn());
      }
    } catch (IndexOutOfBoundsException ignored) {
      // unknown index, dont display line number
    }
  }

  public static class MethodPtr {
    public String className, methodName;
    public String[] erasedParameterTypes;
    public ExecutableElement method;

    public MethodPtr(JavacUtilitiesProvider task, ExecutableElement method) {
      this.method = method;
      Types types = task.getTypes();
      TypeElement parent = (TypeElement) method.getEnclosingElement();
      className = parent.getQualifiedName().toString();
      methodName = method.getSimpleName().toString();
      erasedParameterTypes = new String[method.getParameters().size()];
      for (int i = 0; i < erasedParameterTypes.length; i++) {
        VariableElement param = method.getParameters().get(i);
        TypeMirror type = param.asType();
        TypeMirror erased = types.erasure(type);
        erasedParameterTypes[i] = erased.toString();
      }
    }

    @NonNull
    @Override
    public String toString() {
      return "MethodPtr{"
          + "className='"
          + className
          + '\''
          + ", methodName='"
          + methodName
          + '\''
          + ", erasedParameterTypes="
          + Arrays.toString(erasedParameterTypes)
          + ", method="
          + method
          + '}';
    }
  }

  /**
   * Gets the diagnostics of the current compile task
   *
   * @param task the current compile task where the diagnostic is retrieved
   * @param cursor the current cursor position
   * @return null if no diagnostic is found
   */
  @Nullable
  public static Diagnostic<? extends JavaFileObject> getDiagnostic(CompileTask task, long cursor) {
    return getDiagnostic(task.diagnostics, cursor);
  }

  public static Diagnostic<? extends JavaFileObject> getDiagnostic(
      List<Diagnostic<? extends JavaFileObject>> diagnostics, long cursor) {
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
      if (diagnostic.getStartPosition() <= cursor && cursor < diagnostic.getEndPosition()) {
        return diagnostic;
      }
    }
    return null;
  }

  public static JCDiagnostic getJCDiagnostic(List<JCDiagnostic> diagnostics, long cursor) {
    for (JCDiagnostic diagnostic : diagnostics) {
      if (diagnostic.getStartPosition() <= cursor && cursor < diagnostic.getEndPosition()) {
        return diagnostic;
      }
    }
    return null;
  }

  public static DiagnosticWrapper getDiagnosticWrapper(
      List<DiagnosticWrapper> diagnostics, long start, long end) {
    if (diagnostics == null) {
      return null;
    }
    DiagnosticWrapper current = null;
    for (DiagnosticWrapper diagnostic : diagnostics) {
      if (diagnostic.getStartPosition() <= start && end <= diagnostic.getEndPosition()) {
        if (current == null
            || diagnostic.getStartPosition() < current.getStartPosition()
                && diagnostic.getEndPosition() > current.getEndPosition()) {
          current = diagnostic;
        }
      }
    }

    if (current != null) {
      return current;
    }

    // fallback to start and end separately
    current = getDiagnosticWrapper(diagnostics, start);
    if (current != null) {
      return current;
    }

    return getDiagnosticWrapper(diagnostics, end);
  }

  @Nullable
  public static DiagnosticWrapper getDiagnosticWrapper(
      List<DiagnosticWrapper> diagnostics, long cursor) {
    if (diagnostics == null) {
      return null;
    }

    for (DiagnosticWrapper diagnostic : diagnostics) {
      if (diagnostic.getStartPosition() <= cursor && cursor <= diagnostic.getEndPosition()) {
        return diagnostic;
      }
    }

    return null;
  }

  @Nullable
  public static DiagnosticWrapper getXmlDiagnosticWrapper(
      List<DiagnosticWrapper> diagnostics, int line) {
    if (diagnostics == null) {
      return null;
    }
    for (DiagnosticWrapper diagnostic : diagnostics) {
      if (diagnostic.getLineNumber() - 1 == line) {
        return diagnostic;
      }
    }
    return null;
  }

  @Nullable
  public static ClientCodeWrapper.DiagnosticSourceUnwrapper getDiagnosticSourceUnwrapper(
      Diagnostic<?> diagnostic) {
    if (diagnostic instanceof DiagnosticWrapper) {
      if (((DiagnosticWrapper) diagnostic).getExtra()
          instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper) {
        return (ClientCodeWrapper.DiagnosticSourceUnwrapper)
            ((DiagnosticWrapper) diagnostic).getExtra();
      }
    }
    if (diagnostic instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper) {
      return (ClientCodeWrapper.DiagnosticSourceUnwrapper) diagnostic;
    }
    return null;
  }

  @NonNull
  public static MethodPtr findMethod(JavacUtilitiesProvider task, long position) {
    Trees trees = task.getTrees(); // MTrees.instance(task.getTask());
    Tree tree = new FindMethodDeclarationAt(trees).scan(task.root(), position);
    TreePath path = trees.getPath(task.root(), tree);
    ExecutableElement method = (ExecutableElement) trees.getElement(path);
    return new MethodPtr(task, method);
  }

  @NonNull
  public static String extractExceptionName(String message) {
    Matcher matcher = UNREPORTED_EXCEPTION.matcher(message);
    if (!matcher.find()) {
      return "";
    }
    String group = matcher.group(1);
    if (group == null) {
      return "";
    }
    return group;
  }

  public static DiagnosticWrapper modifyDiagnostic(
      JavacUtilitiesProvider task, Diagnostic<? extends JavaFileObject> diagnostic) {
    DiagnosticWrapper wrapped = new DiagnosticWrapper(diagnostic);

    if (diagnostic instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper) {
      Trees trees = MTrees.instance(task.getTask());
      SourcePositions positions = trees.getSourcePositions();

      JCDiagnostic jcDiagnostic = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) diagnostic).d;
      JCDiagnostic.DiagnosticPosition diagnosticPosition = jcDiagnostic.getDiagnosticPosition();
      JCTree tree = diagnosticPosition.getTree();

      if (tree != null) {
        TreePath treePath = trees.getPath(task.root(), tree);
        if (treePath == null) {
          return wrapped;
        }
        String code = jcDiagnostic.getCode();

        long start = diagnostic.getStartPosition();
        long end = diagnostic.getEndPosition();
        switch (code) {
          case ErrorCodes.MISSING_RETURN_STATEMENT:
            TreePath block = TreeUtil.findParentOfType(treePath, BlockTree.class);
            if (block != null) {
              // show error span only at the end parenthesis
              end = positions.getEndPosition(task.root(), block.getLeaf()) + 1;
              start = end - 2;
            }
            break;
          case ErrorCodes.DEPRECATED:
            if (treePath.getLeaf().getKind() == Tree.Kind.METHOD) {
              MethodTree methodTree = (MethodTree) treePath.getLeaf();
              if (methodTree.getBody() != null) {
                start = positions.getStartPosition(task.root(), methodTree);
                end = positions.getStartPosition(task.root(), methodTree.getBody());
              }
            }
            break;
        }

        wrapped.setStartPosition(start);
        wrapped.setEndPosition(end);
      }
    }
    return wrapped;
  }
}
