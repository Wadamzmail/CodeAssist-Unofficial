package com.tyron.code.ui.editor.snippets

import com.tyron.code.ui.editor.IDEEditor
import com.tyron.builder.project.IProjectManager
import io.github.rosemoe.sora.widget.snippet.variable.FileBasedSnippetVariableResolver

/**
 * Resolver for resolving snippet variables related to the file opened in an editor.
 *
 * @author Akash Yadav
 */
class FileVariableResolver(editor: IDEEditor) : FileBasedSnippetVariableResolver(), AbstractSnippetVariableResolver {

  var editor: IDEEditor? = editor
    private set

  companion object {
    private const val TM_FILENAME = "TM_FILENAME"
    private const val TM_FILENAME_BASE = "TM_FILENAME_BASE"
    private const val TM_DIRECTORY = "TM_DIRECTORY"
    private const val TM_FILEPATH = "TM_FILEPATH"
    private const val RELATIVE_FILEPATH = "RELATIVE_FILEPATH"
  }

  override fun resolve(name: String): String {
    val file = editor?.file ?: return ""
    return when (name) {
      TM_FILENAME -> file.name
      TM_FILENAME_BASE -> file.nameWithoutExtension
      TM_DIRECTORY -> file.parentFile?.absolutePath ?: ""
      TM_FILEPATH -> file.absolutePath
      RELATIVE_FILEPATH -> {
      val projectDir = IProjectManager.getInstance().projectDir
        if (projectDir != null && file.startsWith(projectDir)) {
          file.relativeTo(projectDir).path
        } else {
        file.absolutePath
        }
      }
      else -> ""
    }
  }

  override fun getResolvableNames(): Array<String> {
    return arrayOf(TM_FILENAME, TM_FILENAME_BASE, TM_DIRECTORY, TM_FILEPATH, RELATIVE_FILEPATH)
  }

  override fun close() {
    editor = null
  }
}
