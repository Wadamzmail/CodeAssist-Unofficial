package com.tyron.completion.java.action.quickfix;

import androidx.annotation.NonNull;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.editor.Editor;
import java.io.File;
import javax.tools.Diagnostic;

public abstract class ExceptionsQuickFix extends AnAction {

  public static final String ERROR_CODE =
      "compiler.err.unreported.exception.need.to.catch.or" + ".throw";

  @Override
  public void update(@NonNull AnActionEvent event) {
    event.getPresentation().setVisible(false);

    if (!ActionPlaces.EDITOR.equals(event.getPlace())) {
      return;
    }

    Diagnostic<?> diagnostic = event.getData(CommonDataKeys.DIAGNOSTIC);
    if (diagnostic == null) {
      return;
    }

    if (!ERROR_CODE.equals(diagnostic.getCode())) {
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

    if (currentPath == null) {
      return;
    }

    event.getPresentation().setVisible(true);
  }

  @Override
  public abstract void actionPerformed(@NonNull AnActionEvent e);
}
