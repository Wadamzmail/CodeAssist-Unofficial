package com.tyron.completion.java.action.quickfix;

import static com.tyron.completion.java.util.DiagnosticUtil.findMethod;

import androidx.annotation.NonNull;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.common.util.ThreadUtil;
import com.tyron.completion.java.R;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.completion.java.provider.JavacUtilitiesProvider;
import com.tyron.completion.java.rewrite.AddException;
import com.tyron.completion.java.rewrite.JavaRewrite2;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.java.util.ElementUtil;
import com.tyron.completion.util.RewriteUtil;
import com.tyron.editor.Editor;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public class AddThrowsAction extends ExceptionsQuickFix {

  public static final String ID = "javaAddThrowsQuickFix";

  @Override
  public void update(@NonNull AnActionEvent event) {
    super.update(event);

    Presentation presentation = event.getPresentation();
    if (!presentation.isVisible()) {
      return;
    }

    presentation.setVisible(false);
    Diagnostic<?> diagnostic = event.getData(CommonDataKeys.DIAGNOSTIC);
    if (diagnostic == null) {
      return;
    }

    Editor editor = event.getRequiredData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    File file = event.getRequiredData(CommonDataKeys.FILE);
    if (file == null) return;

    CompilationInfo compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());
    if (unit == null) return;
    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);

    TreePath surroundingPath = ActionUtil.findSurroundingPath(currentPath);
    if (surroundingPath == null) {
      return;
    }
    if (surroundingPath.getLeaf() instanceof LambdaExpressionTree) {
      return;
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
    presentation.setText(event.getDataContext().getString(R.string.menu_quickfix_add_throws_title));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    File file = e.getData(CommonDataKeys.FILE);
    Diagnostic<?> diagnostic = e.getData(CommonDataKeys.DIAGNOSTIC);
    CompilationInfo compilationInfo = e.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());
    if (unit == null) return;
    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);
    String exceptionName =
        DiagnosticUtil.extractExceptionName(diagnostic.getMessage(Locale.ENGLISH));

    ThreadUtil.runOnBackgroundThread(
        () -> {
          AtomicReference<JavaRewrite2> rewrite = new AtomicReference<>();
          rewrite.set(
              performInternal(
                  new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()),
                  exceptionName,
                  diagnostic));
          JavaRewrite2 r = rewrite.get();
          if (r != null) {
            RewriteUtil.performRewrite(
                editor,
                file,
                new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()),
                r);
          }
        });
  }

  private JavaRewrite2 performInternal(
      JavacUtilitiesProvider task, String exceptionName, Diagnostic<?> diagnostic) {
    if (task == null) {
      return null;
    }

    DiagnosticUtil.MethodPtr needsThrow = findMethod(task, diagnostic.getPosition());
    Element classElement = needsThrow.method.getEnclosingElement();
    TypeElement classTypeElement = (TypeElement) classElement;
    TypeMirror superclass = classTypeElement.getSuperclass();
    TypeElement superClassElement = (TypeElement) task.getTypes().asElement(superclass);
    if (!ElementUtil.isMemberOf(task, needsThrow.method, superClassElement)) {
      return new AddException(
          needsThrow.className,
          needsThrow.methodName,
          needsThrow.erasedParameterTypes,
          exceptionName);
    }
    return null;
  }
}
