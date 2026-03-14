package com.tyron.completion.model.snippets

/**
 * Marker interface for snippet scopes.
 *
 * @author Akash Yadav
 */
interface ISnippetScope {

  /**
   * The filename for the snippet. This is used to resolve the snippet file stored at location
   * `'data/editor/<lang>/snippets.<filename>.json'`
   */
  val filename: String
}
