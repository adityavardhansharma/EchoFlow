package com.echoflow.data

internal class DictationConfiguration(
    val model: String,
    val key: String,
    val cloud: Boolean,
    val hinglish: Boolean,
) {
    val ready: Boolean get() = cloud && key.isNotBlank()
}
