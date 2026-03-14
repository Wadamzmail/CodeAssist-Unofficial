package com.tyron.kotlin.completion.resolve.lazy

enum class BodyResolveMode {
    // All body statements are analyzed, diagnostics included
    FULL,

    // Analyzes only dependent statements, including only used declaration statements, does not perform control flow analysis
    PARTIAL,

}
