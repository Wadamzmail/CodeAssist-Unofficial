package com.tyron.completion.java.action.quickfix;

import androidx.annotation.NonNull;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.common.util.ThreadUtil;
import com.tyron.completion.java.R;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.completion.java.rewrite.AddTryCatch;
import com.tyron.completion.java.rewrite.JavaRewrite2;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.util.RewriteUtil;
import com.tyron.editor.Editor;
import java.io.File;
import java.util.Locale;
import javax.tools.Diagnostic;

public class SurroundWithTryCatchAction extends ExceptionsQuickFix {

  public static final String ID = "javaSurroundWithTryCatchQuickFix";

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
    if (surroundingPath.getLeaf() instanceof TryTree) {
      return;
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
    presentation.setText(
        event.getDataContext().getString(R.string.menu_quickfix_surround_try_catch_title));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
    File file = e.getRequiredData(CommonDataKeys.FILE);
    Diagnostic<?> diagnostic = e.getRequiredData(CommonDataKeys.DIAGNOSTIC);
    CompilationInfo compilationInfo = e.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());
    if (unit == null) return;
    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);
    TreePath surroundingPath = ActionUtil.findSurroundingPath(currentPath);
    String exceptionName =
        DiagnosticUtil.extractExceptionName(diagnostic.getMessage(Locale.ENGLISH));

    if (surroundingPath == null) {
      return;
    }

    ThreadUtil.runOnBackgroundThread(
        () -> {
          JavaRewrite2 r = performInternal(file, exceptionName, surroundingPath);
          RewriteUtil.performRewrite(
              editor,
              file,
              new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()),
              r);
        });
  }

  private JavaRewrite2 performInternal(File file, String exceptionName, TreePath surroundingPath) {
    Tree leaf = surroundingPath.getLeaf();
    JCTree tree = (JCTree) leaf;

    CompilationUnitTree root = surroundingPath.getCompilationUnit();
    JCTree.JCCompilationUnit compilationUnit = (JCTree.JCCompilationUnit) root;
    EndPosTable endPositions = compilationUnit.endPositions;

    int start = tree.getStartPosition();
    int end = tree.getEndPosition(endPositions);

    String contents = leaf.toString();
    return new AddTryCatch(file.toPath(), contents, start, end, exceptionName);
  }
}
