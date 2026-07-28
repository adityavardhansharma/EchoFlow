package com.echoflow.data

/**
 * The app's top-level surface. Chat and Imagine are not two feature sets — they are two
 * *shapes* of interaction, which is why they get separate composers, result presentation
 * and history rather than sharing one screen with a menu toggle:
 *
 *  - [Chat] is turn-taking. You ask, the model answers, and the answer is prose you read
 *    and move past. Every chat capability (search, research, browser, artifacts, the Echo
 *    modes) is a variation on "make the answer better".
 *  - [Imagine] is a creative loop. Prompt, render, judge, refine. The output is an artifact
 *    you keep, with settings that persist across turns and a result that wants to be big.
 *
 * A conversation is stamped with the mode it was created in and never changes sides — see
 * [ChatThread.kind]. Mode is lateral state, not a navigation destination: system back never
 * switches it.
 */
enum class AppMode(val storageKey: String) {
    Chat("chat"),
    Imagine("imagine");

    companion object {
        /** Unknown or absent values fall back to [Chat] — the mode every install starts in. */
        fun fromStorage(value: String?): AppMode =
            entries.firstOrNull { it.storageKey == value } ?: Chat
    }
}

/**
 * Where a mode was left when the user switched away from it.
 *
 * [Blank] exists because a blank composer is a *position* — somewhere the user deliberately
 * navigated to by starting something new — and not the absence of one. Treating it as
 * "nothing to remember" is what makes a mode silently reopen an old conversation instead of
 * the fresh one you were looking at.
 */
sealed interface ModePosition {
    data class Thread(val chatId: String) : ModePosition
    data object Blank : ModePosition
    data object Unset : ModePosition
}
