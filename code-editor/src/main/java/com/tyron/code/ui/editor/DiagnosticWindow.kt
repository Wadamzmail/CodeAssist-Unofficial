package com.tyron.code.ui.editor

//import com.tyron.builder.model.DiagnosticWrapper
import java.util.Locale

/**
 * Popup window used to show diagnostic messages.
 *
 * @author Akash Yadav
 */
class DiagnosticWindow(editor: IDEEditor) : BaseEditorWindow(editor) {
  /**
   * Show the given diagnostic item.
   *
   * @param diagnostic The diagnostic item to show.
   */
  fun showDiagnostic(diagnostic: String?) {
    if (diagnostic == null) {
      if (isShowing) {
        dismiss()
      }
      return
    }
    val message = diagnostic//.getMessage(Locale.getDefault())
    text.text = message
    displayWindow()
  }
}
