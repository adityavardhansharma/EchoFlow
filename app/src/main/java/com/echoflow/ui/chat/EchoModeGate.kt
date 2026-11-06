package com.echoflow.ui.chat

import com.echoflow.data.ChatMode

/**
 * Echo Agents, Adviser and Fusion run on OpenRouter server tools, so they share one rule: no
 * direct provider, and (except Fusion, which brings its own panel) no on-device main model.
 * Returns why [mode] can't run with the picked model, or null when it can.
 */
internal object EchoModeGate {
    fun modelError(mode: ChatMode, isLocal: Boolean, customProviderActive: Boolean): String? {
        val name = when (mode) {
            ChatMode.EchoAgent -> "Echo Agents"
            ChatMode.EchoAdviser -> "Echo Adviser"
            ChatMode.EchoFusion -> "Echo Fusion"
            else -> return null
        }
        if (isLocal && mode != ChatMode.EchoFusion) {
            val what = if (mode == ChatMode.EchoAgent) "a cloud main model" else "a cloud model"
            return "$name needs $what. Pick one from the model selector."
        }
        if (customProviderActive) return "$name uses OpenRouter server tools. Pick an OpenRouter Cloud model first."
        return null
    }
}
