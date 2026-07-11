package com.echoflow.data

/** Mutually exclusive per-message chat capability selected from the input "+" menu. */
sealed interface ChatMode {
    data object Normal : ChatMode
    data object WebSearch : ChatMode
    data object DeepResearch : ChatMode
    data object DataAgent : ChatMode
    data object EchoAdviser : ChatMode
    data object EchoFusion : ChatMode
    data object EchoAgent : ChatMode
    data object BrowserFlow : ChatMode
    data object Artifact : ChatMode
    data object ImageGen : ChatMode
}

