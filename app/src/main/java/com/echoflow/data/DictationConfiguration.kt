package com.echoflow.data

internal class DictationConfiguration(
    val model: String,
    val key: String,
    val hinglish: Boolean,
) {
    val ready: Boolean get() = key.isNotBlank()
}
