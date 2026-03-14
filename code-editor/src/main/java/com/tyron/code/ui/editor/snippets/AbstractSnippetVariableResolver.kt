package com.tyron.code.ui.editor.snippets

import io.github.rosemoe.sora.widget.snippet.variable.ISnippetVariableResolver

/**
 * Base class for snippet variable resolvers.
 *
 * @author Akash Yadav
 */
interface AbstractSnippetVariableResolver : ISnippetVariableResolver, AutoCloseable {
  override fun close() {
  }
}
