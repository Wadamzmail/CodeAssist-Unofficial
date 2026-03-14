package com.tyron.completion.model.signatures

import com.tyron.completion.model.CancellableRequestParams
import com.tyron.completion.model.Position
import com.tyron.common.progress.ICancelChecker
import java.nio.file.Path
import java.util.*

data class ParameterInformation(var label: String, var documentation: MarkupContent) {
  constructor() : this("", MarkupContent())
}

data class SignatureInformation(
  var label: String,
  var documentation: MarkupContent,
  var parameters: List<ParameterInformation>
) {
  constructor() : this("", MarkupContent(), Collections.emptyList())
}

data class SignatureHelp(
  var signatures: List<SignatureInformation>,
  var activeSignature: Int,
  var activeParameter: Int
)

data class SignatureHelpParams(
  var file: Path,
  var position: Position,
  override val cancelChecker: ICancelChecker
) : CancellableRequestParams
