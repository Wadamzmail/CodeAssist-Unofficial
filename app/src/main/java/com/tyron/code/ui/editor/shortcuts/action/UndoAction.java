package com.tyron.code.ui.editor.shortcuts.action;

import com.tyron.code.ui.editor.shortcuts.ShortcutAction;
import com.tyron.code.ui.editor.shortcuts.ShortcutItem;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.text.Content;
import com.tyron.code.ui.editor.impl.text.rosemoe.ContentWrapper;
com.tyron.code.ui.editor.impl.text.rosemoe.ContentWrapper

public class UndoAction implements ShortcutAction {

  public static final String KIND = "undoAction";

  @Override
  public boolean isApplicable(String kind) {
    return KIND.equals(kind);
  }

  @Override
  public void apply(Editor editor, ShortcutItem item) {
   //Content content = (Content)new ContentWrapper(editor.getContent());
   CodeEditorView ce = (CodeEditorView)editor;
    if (ce.canUndo()) {
      ce.undo();
    }
  }
}
