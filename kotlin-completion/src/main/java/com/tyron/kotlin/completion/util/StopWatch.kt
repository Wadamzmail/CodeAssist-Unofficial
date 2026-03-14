package com.tyron.kotlin.completion.util

import kotlin.time.measureTime

fun <T> logTime(name: String, block: () -> T): T {
    var value: T
    val duration = measureTime { value = block() }
    println("$name: took $duration ms")
    return value
}
