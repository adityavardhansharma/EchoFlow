package com.echoflow.data

internal class DictationConfiguration(
    val model: String,
    val key: String,
    val hinglish: Boolean,
    /** Custom vocabulary terms; empty unless [model] accepts them. */
    val vocabulary: List<String> = emptyList(),
) {
    val ready: Boolean get() = key.isNotBlank()
}
