/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tyron.completion.java.provider

import com.tyron.completion.java.compiler.JavaCompilerService
import com.tyron.completion.java.provider.snippet.JavaSnippetRepository
import com.tyron.completion.java.provider.snippet.JavaSnippetScope
import com.tyron.completion.java.util.CompletionItemFactory.snippetItem
import com.tyron.completion.model.snippets.ISnippet
import io.github.rosemoe.sora.text.TextUtils
import com.sun.source.tree.ClassTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.MethodTree
import com.sun.source.util.TreePath
import java.nio.file.Path
import com.tyron.completion.model.CompletionList

/**
 * Provides snippet completion for Java files.
 *
 * @author Akash Yadav
 * modified by Wadamzmail
 */
class SnippetCompletionProvider(
  compiler: JavaCompilerService
) : BaseCompletionProvider( compiler) {

  override fun complete(
    builder: CompletionList.Builder,
    task: JavacUtilitiesProvider,
    path: TreePath,
    partial: String,
    endsWithParen: Boolean
  ) {
    val scope = findSnippetScope(path,task.parameters.index) ?: return
    val indent = spacesBeforeCursor(task.root().sourceFile.getCharContent(true),task.parameters.index)
    val snippets = mutableListOf<ISnippet>()

    // add global snippets, if any
    JavaSnippetRepository.snippets[JavaSnippetScope.GLOBAL]?.let { snippets.addAll(it) }

    val snippetScope =
      when (scope.leaf) {
        is CompilationUnitTree -> JavaSnippetScope.TOP_LEVEL
        is ClassTree -> JavaSnippetScope.MEMBER
        is MethodTree -> JavaSnippetScope.LOCAL
        else -> null
      }

    // add snippets for the current scope
    snippetScope?.let { JavaSnippetRepository.snippets[it]?.let { list -> snippets.addAll(list) } }

     for (snippet in snippets) {
      builder.addItem(snippetItem(snippet))
     }
  }

  private fun spacesBeforeCursor(charContent: CharSequence?,cursor: Long): Int {
    charContent ?: return 0
    var start = cursor.toInt()
    while (start >= 0) {
      val c = charContent[start]
      if (c == '\n' || !c.isWhitespace()) {
        break
      }
      --start
    }
    return TextUtils.countLeadingSpaceCount(charContent.substring(start, cursor.toInt()),4)
  }

  private fun findSnippetScope(path: TreePath?, cursor: Long): TreePath? {
    var scope = path
    while (scope != null) {
      if (scope.leaf.let { it is CompilationUnitTree || it is ClassTree || it is MethodTree }) {
        return scope
      }
      scope = scope.parentPath
    }
    return null
  }
}
