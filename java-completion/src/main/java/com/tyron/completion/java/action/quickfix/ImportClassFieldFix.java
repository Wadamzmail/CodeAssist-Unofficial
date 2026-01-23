package com.tyron.completion.java.action.quickfix;

import android.app.AlertDialog;
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
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.completion.java.R;
import com.tyron.completion.java.ShortNamesCache;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.completion.java.rewrite.AddImport;
import com.tyron.completion.java.rewrite.JavaRewrite2;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.util.RewriteUtil;
import com.tyron.editor.Editor;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ImportClassFieldFix extends AnAction {

  public static final String ID = "javaImportClassFieldFix";

  public static final String ERROR_CODE = "compiler.err.doesnt.exist";

  @Override
  public void update(@NonNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setVisible(false);

    if (!ActionPlaces.EDITOR.equals(event.getPlace())) {
      return;
    }

    DiagnosticWrapper diagnostic = event.getData(CommonDataKeys.DIAGNOSTIC);
    if (diagnostic == null) {
      return;
    }

    Editor editor = event.getRequiredData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    Project project = editor.getProject();
    if (project == null) return;
    File file = event.getRequiredData(CommonDataKeys.FILE);
    if (file == null) return;

    //    ClientCodeWrapper.DiagnosticSourceUnwrapper diagnosticSourceUnwrapper =
    //        DiagnosticUtil.getDiagnosticSourceUnwrapper(diagnostic);
    //    if (diagnosticSourceUnwrapper == null) {
    //      return;
    //    }

    if (!ERROR_CODE.equals(diagnostic.getCode())) {
      return;
    }

    JCDiagnostic jcDiagnostic = (JCDiagnostic) diagnostic.getExtra();
    if (jcDiagnostic == null) return;

    CompilationInfo compilationInfo = event.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());
    if (unit == null) return;
    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);

    String simpleName = String.valueOf(jcDiagnostic.getArgs()[0]);
    List<String> classNames = new ArrayList<>();
    for (String qualifiedName : getAllClassNames(editor)) {
      if (qualifiedName.endsWith("." + simpleName)) {
        classNames.add(qualifiedName);
      }
    }

    if (classNames.isEmpty()) {
      return;
    }

    if (classNames.size() > 1) {
      presentation.setText(event.getDataContext().getString(R.string.import_class_title));
    } else {
      String format =
          event
              .getDataContext()
              .getString(
                  R.string.import_class_name,
                  ActionUtil.getSimpleName(classNames.iterator().next()));
      presentation.setText(format);
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    DiagnosticWrapper diagnostic = e.getRequiredData(CommonDataKeys.DIAGNOSTIC);
    //    diagnostic = DiagnosticUtil.getDiagnosticSourceUnwrapper(diagnostic);
    if (diagnostic == null) {
      return;
    }
    JCDiagnostic d = (JCDiagnostic) diagnostic.getExtra();
    if (d == null) return;

    Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
    //    JCDiagnostic d = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) diagnostic).d;
    String simpleName = String.valueOf(d.getArgs()[0]);
    Project project = editor.getProject();
    Path file = e.getRequiredData(CommonDataKeys.FILE).toPath();
    CompilationInfo compilationInfo = e.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;
    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toFile().toURI());
    if (unit == null) return;
    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);

    boolean isField = simpleName.contains(".");
    String searchName = simpleName;
    if (isField) {
      searchName = searchName.substring(0, searchName.indexOf('.'));
    }

    Map<String, JavaRewrite2> map = new TreeMap<>();
    for (String qualifiedName : getAllClassNames(editor)) {
      if (qualifiedName.endsWith("." + simpleName)) {
        if (qualifiedName.endsWith("." + searchName)) {
          if (isField) {
            qualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
            qualifiedName += simpleName;
          }
          String name = e.getDataContext().getString(R.string.import_class_name, qualifiedName);
          JavaRewrite2 addImport = new AddImport(file.toFile(), qualifiedName);
          map.put(name, addImport);
          // throw new UnsupportedOperationException();
        }
      }
    }

    if (map.size() == 1) {
      RewriteUtil.performRewrite(
          editor,
          file.toFile(),
          new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()),
          map.values().iterator().next());
    } else {
      String[] titles = map.keySet().toArray(new String[0]);
      new AlertDialog.Builder(e.getDataContext())
          .setTitle(R.string.import_class_title)
          .setItems(
              titles,
              (di, w) -> {
                JavaRewrite2 rewrite = map.get(titles[w]);
                RewriteUtil.performRewrite(
                    editor,
                    file.toFile(),
                    new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()),
                    rewrite);
              })
          .setNegativeButton(android.R.string.cancel, null)
          .show();
    }
  }

  public String[] getAllClassNames(Editor editor) {
    Module module = editor.getProject().getModule(editor.getCurrentFile());
    ShortNamesCache cache = ShortNamesCache.getInstance(module);
    return cache.getAllClassNames();
  }

  public Set<String> publicTopLevelTypes(Editor editor) {
    Set<String> classes = new HashSet<>();
    Module mCurrentModule = editor.getProject().getModule(editor.getCurrentFile());
    for (Module module : editor.getProject().getDependencies(mCurrentModule)) {
      if (module instanceof JavaModule) {
        classes.addAll(((JavaModule) module).getAllClasses());
      }
    }
    return classes;
  }
}
