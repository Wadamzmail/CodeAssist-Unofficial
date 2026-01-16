package com.tyron.diagnostics;

import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.builder.project.api.Module;
import java.io.File;
import java.util.List;

/** Implementations may provide their own diagnostics for the specified file */
public interface DiagnosticProvider {

  // List<? extends Diagnostic<?>> getDiagnostics(Module module, File file);
  List<JCDiagnostic> getDiagnostics(Module module, File file);
}
