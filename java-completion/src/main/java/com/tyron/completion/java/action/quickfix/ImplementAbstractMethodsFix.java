package com.tyron.completion.java.action.quickfix;

import androidx.annotation.NonNull;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.completion.java.R;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.completion.java.rewrite.ImplementAbstractMethods;
import com.tyron.completion.java.rewrite.JavaRewrite2;
import com.tyron.completion.util.RewriteUtil;
import com.tyron.editor.Editor;
import java.io.File;

public class ImplementAbstractMethodsFix extends AnAction {

  public static final String ID = "javaImplementAbstractMethodsFix";

  public static final String ERROR_CODE = "compiler.err.does.not.override.abstract";

  @Override
  public void update(@NonNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setVisible(false);

    DiagnosticWrapper diagnostic = event.getData(CommonDataKeys.DIAGNOSTIC);
    if (diagnostic == null) {
      System.out.println("IAMA:diagnostic null");
      return;
    }
    System.out.println(diagnostic.toString());

    if (!ActionPlaces.EDITOR.equals(event.getPlace())) {
      return;
    }

    //    ClientCodeWrapper.DiagnosticSourceUnwrapper diagnosticSourceUnwrapper =
    //        DiagnosticUtil.getDiagnosticSourceUnwrapper(diagnostic);
    //    if (diagnosticSourceUnwrapper == null) {
    //    System.out.println("IAMA: no unwrapped diagnostic");
    //      return;
    //    }

    if (!ERROR_CODE.equals(diagnostic.getCode())) {
      System.out.println(
          "IAMA:Code:"
              + diagnostic.getCode()
              + " is not equals:compiler.err.does.not.override.abstract");
      return;
    }

    Editor editor = event.getRequiredData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    File file = event.getRequiredData(CommonDataKeys.FILE);
    if (file == null) return;

    CompilationInfo compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;

    presentation.setVisible(true);
    presentation.setText(
        event.getDataContext().getString(R.string.menu_quickfix_implement_abstract_methods_title));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    File file = e.getData(CommonDataKeys.FILE);
    DiagnosticWrapper diagnostic = e.getData(CommonDataKeys.DIAGNOSTIC);
    //    ClientCodeWrapper.DiagnosticSourceUnwrapper diagnosticSourceUnwrapper =
    //        DiagnosticUtil.getDiagnosticSourceUnwrapper(diagnostic);
    //    if (diagnosticSourceUnwrapper == null) {
    //      return;
    //    }
    CompilationInfo compilationInfo = e.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());
    if (unit == null) return;
    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);

    //    JCDiagnostic jcDiagnostic;
    //    if (diagnostic instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper) {
    //            jcDiagnostic = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) diagnostic).d;
    //          } else {
    //            jcDiagnostic = (JCDiagnostic) diagnostic;
    //      }

    JCDiagnostic jcDiagnostic = (JCDiagnostic) diagnostic.getExtra();

    //    JCDiagnostic jcDiagnostic = diagnosticSourceUnwrapper.d;
    JavaRewrite2 rewrite = new ImplementAbstractMethods(jcDiagnostic);
    RewriteUtil.performRewrite(
        editor,
        file,
        new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()),
        rewrite);
  }
}
