package com.tyron.code.ui.editor

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import org.slf4j.LoggerFactory

/**
 * Abstract class for all [IDEEditor] popup windows.
 *
 * @author Akash Yadav
 * implemented by Wadamzmail 
 */
abstract class AbstractPopupWindow(editor: CodeEditor, features: Int) :
  EditorPopupWindow(editor, features) {

  companion object {

    private val log = LoggerFactory.getLogger(AbstractPopupWindow::class.java)
  }

  override fun show() {
    (editor as? IDEEditor)?.ensureWindowsDismissed()
 //    editor.hideEditorWindows()
    if (!editor.isAttachedToWindow) {
      log.error("Trying to show popup window '{}' when editor is not attached to window",
        javaClass.name)
      return
    }

    super.show()
  }

  override fun isShowing(): Boolean {
    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    return popup?.isShowing ?: false
  }
}
