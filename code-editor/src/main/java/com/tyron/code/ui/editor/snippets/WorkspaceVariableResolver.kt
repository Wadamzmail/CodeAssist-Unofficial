package com.tyron.code.ui.editor.snippets

import com.tyron.builder.project.IProjectManager
import io.github.rosemoe.sora.widget.snippet.variable.WorkspaceBasedSnippetVariableResolver

/**
 * Resolver for resolving snippet variables related to the opened workspace folder (project).
 *
 * @author Akash Yadav
 */
class WorkspaceVariableResolver :
  WorkspaceBasedSnippetVariableResolver(), AbstractSnippetVariableResolver {

  companion object {

    private const val WORKSPACE_NAME = "WORKSPACE_NAME"
    private const val WORKSPACE_FOLDER = "WORKSPACE_FOLDER"
  }

  override fun resolve(name: String): String {
    val directory = IProjectManager.getInstance().projectDir
    if(directory==null)return ""
    return when (name) {
      WORKSPACE_NAME -> directory.name
      WORKSPACE_FOLDER -> directory.absolutePath
      else -> ""
    }
  }

  override fun getResolvableNames(): Array<String> {
    return arrayOf(WORKSPACE_NAME, WORKSPACE_FOLDER)
  }
}
