package com.tyron.code.ui.main.action.project;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import androidx.annotation.NonNull;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.code.ui.editor.impl.text.rosemoe.CodeEditorView;
import com.tyron.fileeditor.api.FileEditor;
import com.tyron.fileeditor.api.TextEditor;

public class SearchAction extends AnAction {

  @Override
  public void update(@NonNull AnActionEvent event) {
    event.getPresentation().setVisible(false);
    if (!ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace())) {
      return;
    }

    FileEditor fileEditor = event.getData(CommonDataKeys.FILE_EDITOR_KEY);
    if (fileEditor == null) {
      return;
    }

    event.getPresentation().setVisible(true);
    event.getPresentation().setText("Search");
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    FileEditor fileEditor = e.getRequiredData(CommonDataKeys.FILE_EDITOR_KEY);
    if (fileEditor instanceof TextEditor) {
      CodeEditorView cEditor = (CodeEditorView) ((TextEditor) fileEditor).getEditor();
      if (cEditor == null) return;
      cEditor.getSearcher().stopSearch();
      cEditor.beginSearchMode();
    }
  }

  private Context getActivityContext(Context context) {
    Context current = context;
    while (current != null) {
      if (current instanceof Activity) {
        return current;
      }
      if (current instanceof ContextWrapper) {
        current = ((ContextWrapper) current).getBaseContext();
      } else {
        current = null;
      }
    }
    return null;
  }
}
