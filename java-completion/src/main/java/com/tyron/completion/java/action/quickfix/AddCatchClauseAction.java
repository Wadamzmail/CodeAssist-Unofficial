package com.tyron.completion.java.action.quickfix;

import androidx.annotation.NonNull;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
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
import com.tyron.completion.java.rewrite.AddCatchClause;
import com.tyron.completion.java.rewrite.JavaRewrite2;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.util.RewriteUtil;
import com.tyron.editor.Editor;
import java.io.File;
import java.util.Locale;
import javax.tools.Diagnostic;

public class AddCatchClauseAction extends ExceptionsQuickFix {

  public static final String ID = "javaAddCatchClauseQuickFix";

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

    if (!(surroundingPath.getLeaf() instanceof TryTree)) {
      return;
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
    presentation.setText(
        event.getDataContext().getString(R.string.menu_quickfix_add_catch_clause_title));
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
    CompilationUnitTree root = surroundingPath.getCompilationUnit();
    JCTree.JCCompilationUnit compilationUnit = (JCTree.JCCompilationUnit) root;
    EndPosTable endPositions = compilationUnit.endPositions;

    TryTree tryTree = (TryTree) surroundingPath.getLeaf();
    CatchTree catchTree = tryTree.getCatches().get(tryTree.getCatches().size() - 1);
    JCTree.JCCatch jcCatch = (JCTree.JCCatch) catchTree;

    int start = (int) jcCatch.getEndPosition(endPositions);
    return new AddCatchClause(file.toPath(), start, exceptionName);
  }
}
