package com.echoflow.data

/** Safe presentation state: never exposes credentials to the connection card. */
data class OpenRouterConnection(
    val connected: Boolean = false,
    val signedIn: Boolean = false,
    val keyEnding: String = "",
    val hasSavedManualKey: Boolean = false,
)
