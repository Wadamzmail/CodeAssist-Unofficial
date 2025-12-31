package com.tyron.code.ui.editor.shortcuts.action;

import com.tyron.code.ui.editor.shortcuts.ShortcutAction;
import com.tyron.code.ui.editor.shortcuts.ShortcutItem;
import com.tyron.editor.Editor;
import io.github.rosemoe.sora.text.Content;
import com.tyron.code.ui.editor.impl.text.rosemoe.ContentWrapper;

public class RedoAction implements ShortcutAction {

  public static final String KIND = "redoAction";

  @Override
  public boolean isApplicable(String kind) {
    return KIND.equals(kind);
  }

  @Override
  public void apply(Editor editor, ShortcutItem item) {
    Content content = (Content)new ContentWrapper(editor.getContent());
    if (content.canRedo()) {
      content.redo();
    }
  }
}
