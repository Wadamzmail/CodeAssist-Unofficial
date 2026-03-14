package dev.mutwakil.javac;

import com.sun.source.util.DocTrees;
import com.sun.source.util.Trees;
import javax.tools.JavaCompiler.CompilationTask;

public final class MDocTrees {

  private MDocTrees() {}

  public static DocTrees instance(CompilationTask task) {
    return (DocTrees) Trees.instance(task);
  }
}
