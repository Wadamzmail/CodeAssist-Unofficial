package com.tyron.completion.model.snippets

/**
 * A snippet item.
 *
 * @author Akash Yadav
 */
interface ISnippet {

  /** The prefix for the snippet. */
  val prefix: String

  /** A short description about the snippet. */
  val description: String

  /**
   * The snippet body. Each element in this array represents a line of code. The lines are joined
   * and indented before inserting the text.
   */
  val body: Array<String>
}
