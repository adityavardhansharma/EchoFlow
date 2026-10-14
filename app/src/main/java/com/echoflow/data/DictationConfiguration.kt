package com.echoflow.data

internal class DictationConfiguration(
    val model: String,
    val key: String,
    /** Romanize Hindi output (any model) through Sarvam; true only when [sarvamKey] is set. */
    val hinglish: Boolean,
    /** Custom vocabulary terms; empty unless [model] accepts them. */
    val vocabulary: List<String> = emptyList(),
    val sarvamKey: String = "",
    /** Gemini Transcribe runs on the user's Gemini key; [key] is then that key. */
    val geminiDirect: Boolean = false,
    /** Gemini smart mode; false unless [geminiDirect]. */
    val rambler: Boolean = false,
) {
    val ready: Boolean get() = key.isNotBlank()
}
