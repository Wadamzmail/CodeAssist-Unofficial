package com.tyron.completion.java.action.context;

import android.app.AlertDialog;
import androidx.annotation.NonNull;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.Presentation;
import com.tyron.completion.java.R;
import com.tyron.completion.java.action.FindCurrentPath;
import com.tyron.completion.java.hover.HoverProvider;
import com.tyron.completion.java.parse.CompilationInfo;
import com.tyron.completion.java.provider.DefaultJavacUtilitiesProvider;
import com.tyron.editor.Editor;
import java.io.File;
import java.util.List;

public class ViewJavaDocAction extends AnAction {

  @Override
  public void update(@NonNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setVisible(false);

    if (!ActionPlaces.EDITOR.equals(event.getPlace())) {
      return;
    }

    File file = event.getData(CommonDataKeys.FILE);
    if (file == null) {
      return;
    }

    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
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
    if (currentPath == null) return;

    HoverProvider hoverProvider =
        new HoverProvider(new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()));
    List<String> strings =
        hoverProvider.hover(file.toPath().getFileName(), editor.getCaret().getStart());

    if (strings.isEmpty()) {
      return;
    }

    presentation.setVisible(true);
    presentation.setText(event.getDataContext().getString(R.string.menu_action_view_javadoc_title));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    File file = e.getData(CommonDataKeys.FILE);
    CompilationInfo compilationInfo = e.getData(CompilationInfo.COMPILATION_INFO_KEY);
    if (compilationInfo == null) return;

    JCTree.JCCompilationUnit unit = compilationInfo.getCompilationUnit(file.toURI());
    if (unit == null) return;

    int left = editor.getCaret().getStart();
    int right = editor.getCaret().getEnd();
    JavacTaskImpl javacTask = compilationInfo.impl.getJavacTask();
    TreePath currentPath = new FindCurrentPath(javacTask).scan(unit, left, right);
    if (currentPath == null) return;

    HoverProvider hoverProvider =
        new HoverProvider(new DefaultJavacUtilitiesProvider(javacTask, unit, editor.getProject()));
    List<String> strings = hoverProvider.hover(file.toPath(), editor.getCaret().getStart());

    String title = e.getDataContext().getString(R.string.menu_action_view_javadoc_title);

    new AlertDialog.Builder(e.getDataContext())
        .setTitle(title)
        .setMessage(strings.get(0))
        .setPositiveButton(R.string.menu_close, null)
        .show();
  }
}
