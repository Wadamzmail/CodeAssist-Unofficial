package com.tyron.completion.java.util;

import static com.tyron.completion.java.util.DiagnosticUtil.MethodPtr;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.tyron.completion.java.FindTypeDeclarationAt;
import com.tyron.completion.java.action.FindMethodDeclarationAt;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.completion.model.Position;
import com.tyron.completion.model.Range;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Akash Yadav
 */
public class CodeActionUtils {

  private static final Pattern NOT_THROWN_EXCEPTION =
      Pattern.compile("^'((\\w+\\.)*\\w+)' is not thrown");
  private static final Pattern UNREPORTED_EXCEPTION =
      Pattern.compile("unreported exception ((\\w+\\.)*\\w+)");
  private static final Logger LOG = LoggerFactory.getLogger(CodeActionUtils.class);

  public static boolean isInMethod(@NonNull JavacUtilitiesProvider task, long cursor) {
    MethodTree method = new FindMethodDeclarationAt(task.getTrees()).scan(task.root(), cursor);
    return method != null;
  }

  public static boolean isBlankLine(@NonNull CompilationUnitTree root, long cursor) {
    LineMap lines = root.getLineMap();
    long line = lines.getLineNumber(cursor);
    long start = lines.getStartPosition(line);
    CharSequence contents;
    try {
      contents = root.getSourceFile().getCharContent(true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (long i = start; i < cursor; i++) {
      if (!Character.isWhitespace(contents.charAt((int) i))) {
        return false;
      }
    }
    return true;
  }

  public static int findPosition(@NonNull JavacUtilitiesProvider task, @NonNull Position position) {
    final LineMap lines = task.root().getLineMap();
    return (int) lines.getPosition(position.getLine() + 1, position.getColumn() + 1);
  }

  @Nullable
  public static String findClassNeedingConstructor(JavacUtilitiesProvider task, Range range) {
    final ClassTree type = findClassTree(task, range);
    if (type == null || hasConstructor(task, type)) {
      return null;
    }
    return qualifiedName(task, type);
  }

  public static ClassTree findClassTree(
      @NonNull JavacUtilitiesProvider task, @NonNull Range range) {
    final long position =
        task.root()
            .getLineMap()
            .getPosition(range.getStart().getLine() + 1, range.getStart().getColumn() + 1);
    return newClassFinder(task).scan(task.root(), position);
  }

  @NonNull
  @Contract("_ -> new")
  public static FindTypeDeclarationAt newClassFinder(@NonNull JavacUtilitiesProvider task) {
    return new FindTypeDeclarationAt(task.getTrees());
  }

  @NonNull
  public static String qualifiedName(@NonNull JavacUtilitiesProvider task, ClassTree tree) {
    final Trees trees = task.getTrees();
    final TreePath path = trees.getPath(task.root(), tree);
    final TypeElement type = (TypeElement) trees.getElement(path);
    return type.getQualifiedName().toString();
  }

  public static boolean hasConstructor(JavacUtilitiesProvider task, @NonNull ClassTree type) {
    for (Tree member : type.getMembers()) {
      if (member instanceof MethodTree) {
        MethodTree method = (MethodTree) member;
        if (isConstructor(task, method)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isConstructor(JavacUtilitiesProvider task, @NonNull MethodTree method) {
    return method.getName().contentEquals("<init>") && !synthetic(task, method);
  }

  public static boolean synthetic(@NonNull JavacUtilitiesProvider task, MethodTree method) {
    return task.getTrees().getSourcePositions().getStartPosition(task.root(), method) != -1;
  }

  @NonNull
  public static MethodPtr findMethod(@NonNull JavacUtilitiesProvider task, @NonNull Range range) {
    final Trees trees = task.getTrees();
    final long position =
        task.root()
            .getLineMap()
            .getPosition(range.getStart().getLine() + 1, range.getStart().getColumn() + 1);
    final MethodTree tree = new FindMethodDeclarationAt(trees).scan(task.root(), position);
    final TreePath path = trees.getPath(task.root(), tree);
    final ExecutableElement method = (ExecutableElement) trees.getElement(path);
    return new MethodPtr(task, method);
  }

  public static String extractNotThrownExceptionName(String message) {
    final Matcher matcher = NOT_THROWN_EXCEPTION.matcher(message);
    if (!matcher.find()) {
      LOG.warn("`{}` doesn't match `{}`", message, NOT_THROWN_EXCEPTION);
      return "";
    }
    return matcher.group(1);
  }

  public static String extractExceptionName(String message) {
    final Matcher matcher = UNREPORTED_EXCEPTION.matcher(message);
    if (!matcher.find()) {
      LOG.warn("`{}` doesn't match `{}`", message, UNREPORTED_EXCEPTION);
      return "";
    }
    return matcher.group(1);
  }

  @NonNull
  public static CharSequence extractRange(@NonNull JavacUtilitiesProvider task, Range range) {
    CharSequence contents;
    try {
      contents = task.root().getSourceFile().getCharContent(true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    int start =
        (int)
            task.root()
                .getLineMap()
                .getPosition(range.getStart().getLine() + 1, range.getStart().getColumn() + 1);
    int end =
        (int)
            task.root()
                .getLineMap()
                .getPosition(range.getEnd().getLine() + 1, range.getEnd().getColumn() + 1);
    return contents.subSequence(start, end);
  }
}
