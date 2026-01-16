package com.tyron.completion.java.diagnostics;

import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.builder.project.api.Module;
import com.tyron.completion.java.compiler.services.NBLog;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.diagnostics.DiagnosticProvider;
import java.io.File;
import java.util.Collections;
import java.util.List;

public class JavaDiagnosticsProvider implements DiagnosticProvider {
  @Override
  // public List<? extends Diagnostic<?>> getDiagnostics(Module module, File file) {
  public List<JCDiagnostic> getDiagnostics(Module module, File file) {
    CompilationInfo compilationInfo = CompilationInfo.get(module.getProject(), file);
    if (compilationInfo == null) {
      return Collections.emptyList();
    }

    // JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());

    return NBLog.instance(compilationInfo.impl.getJavacTask().getContext())
        .getDiagnostics(file.toURI()); // .stream()
    // .map( d ->DiagnosticUtil.modifyDiagnostic(new
    // DefaultJavacUtilitiesProvider(compilationInfo.impl.getJavacTask(), unit, null), d))
    // .collect(Collectors.toList());
  }
}
