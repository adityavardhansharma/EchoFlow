package com.echoflow.data

internal class DictationConfiguration(
    val model: String,
    val key: String,
    /** Romanize Hindi output (any model) through Sarvam; true only when [sarvamKey] is set. */
    val hinglish: Boolean,
    /** Custom vocabulary terms; empty unless [model] accepts them. */
    val vocabulary: List<String> = emptyList(),
    val sarvamKey: String = "",
) {
    val ready: Boolean get() = key.isNotBlank()
}
