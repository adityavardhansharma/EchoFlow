package com.echoflow.ui

data class OpenRouterAuthState(
    val busy: Boolean = false,
    val waitingForBrowser: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)
