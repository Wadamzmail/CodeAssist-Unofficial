package com.tyron.completion.model.signatures

interface SignatureHelpLanguage {

    fun signatureHelp(params: SignatureHelpParams): SignatureHelp =
        unsupportedSignatureHelp()
}

fun unsupportedSignatureHelp(): SignatureHelp =
    SignatureHelp(emptyList(), -1, -1)