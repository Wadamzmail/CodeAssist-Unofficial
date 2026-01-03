package com.tyron.completion.java.action.quickfix;

import androidx.annotation.NonNull;
import com.sun.source.util.TreePath;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.completion.java.action.CommonJavaContextKeys;
import com.tyron.editor.Editor;
import javax.tools.Diagnostic;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.completion.java.rewrite.JavaRewrite2;

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

    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      return;
    }

    event.getPresentation().setVisible(true);
  }

  @Override
  public abstract void actionPerformed(@NonNull AnActionEvent e);
}
