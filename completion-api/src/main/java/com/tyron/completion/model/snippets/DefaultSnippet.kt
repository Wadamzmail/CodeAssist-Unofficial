package com.tyron.completion.model.snippets

/**
 * Java snippet.
 *
 * @author Akash Yadav
 */
data class DefaultSnippet(
  override val prefix: String,
  override val description: String,
  override val body: Array<String>
) : ISnippet {

  constructor(
    prefix: String,
    description: String,
    content: () -> Array<String>
  ) : this(prefix, description, content())

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DefaultSnippet) return false

    if (description != other.description) return false
    if (prefix != other.prefix) return false
    if (!body.contentEquals(other.body)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = prefix.hashCode()
    result = 31 * result + description.hashCode()
    result = 31 * result + body.contentHashCode()
    return result
  }
}
