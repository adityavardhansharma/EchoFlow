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
    data object VideoGen : ChatMode
}


/** How long a [ChatMode] stays on once picked. */
enum class ModeLifetime {
    /** Stays on in its chat until the user turns it off. Parked per chat when they leave. */
    Sticky,

    /** Armed for the next send only, then back to Normal. Never parked. */
    OneSend,

    /** On while its run goes, then back to Normal so a follow-up isn't another paid run. */
    UntilRunEnds,
}

/** Each mode's lifetime, in one place: a new mode only has to pick one here. */
val ChatMode.lifetime: ModeLifetime
    get() = when (this) {
        ChatMode.ImageGen, ChatMode.VideoGen -> ModeLifetime.OneSend
        ChatMode.DeepResearch, ChatMode.DataAgent -> ModeLifetime.UntilRunEnds
        ChatMode.Normal, ChatMode.WebSearch, ChatMode.EchoAdviser, ChatMode.EchoFusion,
        ChatMode.EchoAgent, ChatMode.BrowserFlow, ChatMode.Artifact -> ModeLifetime.Sticky
    }
