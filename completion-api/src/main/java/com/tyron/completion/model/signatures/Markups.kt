package com.tyron.completion.model.signatures

import com.tyron.completion.model.signatures.MarkupKind.PLAIN

data class MarkupContent(var value: String, var kind: MarkupKind) {
  constructor() : this("", PLAIN)
}

enum class MarkupKind {
  PLAIN,
  MARKDOWN
}
