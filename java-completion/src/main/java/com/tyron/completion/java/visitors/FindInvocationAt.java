package com.tyron.completion.java.visitors;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.tyron.common.progress.ICancelChecker;
import dev.mutwakil.javac.*;

public class FindInvocationAt extends TreePathScanner<TreePath, Long> {

  private final JavacTask task;
  private final ICancelChecker cancelChecker;
  private CompilationUnitTree root;

  public FindInvocationAt(JavacTask task, ICancelChecker cancelChecker) {
    this.task = task;
    this.cancelChecker = cancelChecker;
  }

  @Override
  public TreePath visitCompilationUnit(CompilationUnitTree t, Long find) {
    cancelChecker.abortIfCancelled();
    root = t;
    return reduce(super.visitCompilationUnit(t, find), getCurrentPath());
  }

  @Override
  public TreePath visitMethodInvocation(MethodInvocationTree t, Long find) {
    cancelChecker.abortIfCancelled();
    SourcePositions pos = MTrees.instance(task).getSourcePositions();
    long start = pos.getEndPosition(root, t.getMethodSelect()) + 1;
    long end = pos.getEndPosition(root, t) - 1;
    if (start <= find && find <= end) {
      return reduce(super.visitMethodInvocation(t, find), getCurrentPath());
    }
    return super.visitMethodInvocation(t, find);
  }

  @Override
  public TreePath visitNewClass(NewClassTree t, Long find) {
    cancelChecker.abortIfCancelled();
    SourcePositions pos = MTrees.instance(task).getSourcePositions();
    long start = pos.getEndPosition(root, t.getIdentifier()) + 1;
    long end = pos.getEndPosition(root, t) - 1;
    if (start <= find && find <= end) {
      return reduce(super.visitNewClass(t, find), getCurrentPath());
    }
    return super.visitNewClass(t, find);
  }

  @Override
  public TreePath reduce(TreePath a, TreePath b) {
    cancelChecker.abortIfCancelled();
    if (a != null) {
      return a;
    }
    return b;
  }
}
