package com.echoflow.data

internal class DictationConfiguration(
    val model: String,
    val key: String,
    /** Romanize Hindi output (any model) through Sarvam; true only when [sarvamKey] is set. */
    val hinglish: Boolean,
    /** Custom vocabulary terms; empty unless [model] accepts them. */
    val vocabulary: List<String> = emptyList(),
    val sarvamKey: String = "",
    /** Gemini Transcribe or Scribe v2 runs on its maker's API with the user's key; [key] is then that key. */
    val providerDirect: Boolean = false,
    /** The direct route's smart mode: Rambler for Gemini, transcript editing for Scribe. False unless [providerDirect]. */
    val smartMode: Boolean = false,
) {
    val ready: Boolean get() = key.isNotBlank()
}
