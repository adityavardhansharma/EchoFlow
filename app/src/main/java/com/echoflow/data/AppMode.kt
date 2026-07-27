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
