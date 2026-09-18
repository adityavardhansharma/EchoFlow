package com.echoflow.data.memory

import com.echoflow.data.ChatMessage
import java.time.LocalDate

/** Cheap, deterministic guardrails for the cases that must not depend on model tool choice. */
object MemoryPolicy {
    data class Fact(val kind: String, val text: String)
    data class CleanupPlan(val discard: List<RemoteMemory>, val duplicateGroups: Int, val metaMemories: Int) {
        val isEmpty get() = discard.isEmpty()
    }

    private val personalQuestion = Regex(
        "(?i)\\b(?:what(?:'s| is| are) my|when is my|who am i|how old am i|where do i live|" +
            "what do you (?:know|remember) about me|do you (?:know|remember) my|" +
            "people i know|my usual|my favorite|my favourite)\\b"
    )
    private val priorConversation = Regex(
        "(?i)\\b(?:i (?:already )?told you|you (?:already )?(?:know|remember)|" +
            "as i (?:said|mentioned) before|from (?:our|the) (?:last|previous) (?:chat|conversation)|" +
            "continue where we left off|we discussed (?:before|earlier))\\b"
    )
    private val personalizableChoice = Regex(
        "(?i)\\b(?:recommend|suggest|recommendation|what should i (?:watch|read|play|buy|visit|choose|pick)|" +
            "help me (?:choose|pick|decide)|which (?:one|movie|film|show|series|book|game|product|place) should i)\\b"
    )
    private val generalWhichChoice = Regex(
        "(?i)\\bwhich (?:[\\p{L}\\p{N}-]+\\s+){1,5}should i\\b"
    )
    private val selfContainedChoice = Regex(
        "(?i)\\b(?:from|between|among)\\b.*\\b(?:above|below|these|following|options?)\\b|" +
            "\\b(?:the )?(?:two|three) options?\\b"
    )
    private val nonPersonalSuggestion = Regex(
        "(?i)\\b(?:suggest|recommend)\\s+(?:better\\s+)?(?:phrasing|wording|title|titles|edits?|" +
            "rewrites?|corrections?|improvements?|changes?)\\b"
    )
    private val denial = Regex(
        "(?i)\\b(?:i (?:do not|don't) know|i (?:do not|don't|cannot|can't) have access|" +
            "i (?:cannot|can't) tell|you (?:have not|haven't) told me|not in (?:my |the )?(?:saved )?memor)"
    )

    /** Selects a focused memory query only when personal history can affect the response. */
    fun recallQuery(prompt: String, previousAssistant: ChatMessage?): String? {
        val text = prompt.trim()
        if (selfContainedChoice.containsMatchIn(text) || nonPersonalSuggestion.containsMatchIn(text)) return null
        val correctingDenial = previousAssistant?.let { denial.containsMatchIn(it.content) } == true &&
            Regex("(?i)^(?:no[,. ]*)?(?:u|you) do\\b|\\bi told you\\b|\\byou know\\b|\\bremember\\b")
                .containsMatchIn(text)
        if (!personalQuestion.containsMatchIn(text) && !priorConversation.containsMatchIn(text) &&
            !personalizableChoice.containsMatchIn(text) && !generalWhichChoice.containsMatchIn(text) && !correctingDenial) return null
        return focusedQuery(text)
    }

    fun focusedQuery(prompt: String): String {
        val value = prompt.lowercase()
        return when {
            Regex("\\b(?:name|who am i)\\b").containsMatchIn(value) -> "user's name or preferred name"
            Regex("\\b(?:age|how old)\\b").containsMatchIn(value) -> "user's age or date of birth"
            "birthday" in value || "birth date" in value ->
                if ("people i know" in value) "people the user knows and their birthdays"
                else "user's birthday or birth date"
            Regex("\\bwhere do i live\\b|\\bmy (?:home|location)\\b").containsMatchIn(value) -> "user's home location"
            Regex("what do you (?:know|remember) about me").containsMatchIn(value) ->
                "user profile: identity, preferences, work, interests, relationships, and ongoing projects"
            "favorite" in value || "favourite" in value || "my usual" in value ->
                "user's relevant preferences and usual choices: $prompt"
            personalizableChoice.containsMatchIn(value) ->
                "preferences, prior experiences, consumed or owned items, rejections, and constraints relevant to: $prompt"
            else -> "relevant facts from the user's previous conversations: $prompt"
        }.take(1000)
    }

    fun needsProfile(query: String): Boolean = Regex(
        "(?i)\\b(?:user profile|user's name|preferred name|user's age|date of birth|user's home|identity)\\b"
    ).containsMatchIn(query)

    /** Only facts with an unusually low false-positive rate bypass delayed transcript learning. */
    fun durableFacts(message: String, observedOn: LocalDate = LocalDate.now()): List<Fact> {
        val text = message.trim().replace(Regex("\\s+"), " ")
        if (text.length !in 3..1000 || MemoryPrivacy.redact(text) != text) return emptyList()
        val facts = mutableListOf<Fact>()

        Regex("(?i)\\bmy name is\\s+([\\p{L}][\\p{L} .'-]{0,59}?)(?=[,.!?;]|$)").find(text)
            ?.groupValues?.get(1)?.trim()?.takeIf(::plausibleName)
            ?.let { facts += Fact("identity", "The user's name is $it.") }
        Regex("\\b(?:[Pp]lease )?[Cc]all me\\s+([\\p{Lu}][\\p{L} .'-]{0,59}?)(?=[,.!?;]|$)").find(text)
            ?.groupValues?.get(1)?.trim()?.takeIf(::plausibleName)
            ?.let { facts += Fact("identity", "The user prefers to be called $it.") }
        Regex("(?i)\\b(?:i am|i'm)\\s+(1[3-9]|[2-9][0-9]|1[01][0-9])(?:\\s+years? old\\b|(?=\\s*(?:[.!?;]|$|,?\\s+and\\s+i\\b)))").find(text)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.let { facts += Fact("demographic", "The user is $it years old as of $observedOn.") }
        Regex("(?i)\\bi live in\\s+([^,.!?;]{2,80})(?=[,.!?;]|$)").find(text)
            ?.groupValues?.get(1)?.trim()
            ?.let { facts += Fact("location", "The user lives in $it.") }
        Regex("(?i)\\bi prefer\\s+([^.!?;]{2,120})(?=[.!?;]|$)").find(text)
            ?.groupValues?.get(1)?.trim()
            ?.let { facts += Fact("preference", "The user prefers $it.") }
        Regex("(?i)\\bmy favou?rite\\s+([^.!?;]{2,120})(?=[.!?;]|$)").find(text)
            ?.groupValues?.get(1)?.trim()
            ?.let { facts += Fact("preference", "The user's favorite is $it.") }
        Regex("(?i)\\bi(?:'m| am) (?:building|working on)\\s+([^.!?;]{2,140})(?=[.!?;]|$)").find(text)
            ?.groupValues?.get(1)?.trim()
            ?.let { facts += Fact("project", "The user is working on $it.") }

        return facts.distinctBy { normalize(it.text) }.take(3)
    }

    fun isAssistantMetaMemory(text: String): Boolean = listOf(
        Regex("(?i)\\bthe assistant (?:has |can |will |should )?(?:stored|saved|remember|retrieve|recall|know)"),
        Regex("(?i)\\bwhen (?:prompted|asked) to (?:search|use|check) memor"),
        Regex("(?i)^(?:please )?(?:use|call|invoke|run) (?:the )?(?:memory|remember_memory|search_memory) tool\\b"),
    ).any { it.containsMatchIn(text) }

    fun normalize(text: String): String = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Conservative cleanup: exact equivalents plus known assistant-meta garbage, never fuzzy deletion. */
    fun cleanupPlan(memories: List<RemoteMemory>): CleanupPlan {
        val meta = memories.filter { isAssistantMetaMemory(it.text) }
        val useful = memories.filterNot { it in meta }
        val duplicateGroups = useful.groupBy { canonicalMemoryKey(it.text) }.values.filter { it.size > 1 }
        val duplicateDiscards = duplicateGroups.flatMap { group ->
            val keeper = group.maxWithOrNull(compareBy<RemoteMemory> { it.updated }.thenBy { it.text.length })
            group.filterNot { it.id == keeper?.id }
        }
        return CleanupPlan((meta + duplicateDiscards).distinctBy { it.id }, duplicateGroups.size, meta.size)
    }

    internal fun canonicalMemoryKey(text: String): String {
        val normalized = normalize(text)
            .removePrefix("the user s ").removePrefix("the user ").removePrefix("user s ").removePrefix("user ")
            .removePrefix("my ").trim()
        val name = Regex("(?:name is|preferred name is|prefers to be called|called)\\s+(.+)").find(normalized)?.groupValues?.get(1)
        if (!name.isNullOrBlank()) return "identity:name:$name"
        val age = Regex("(?:is |age is )?(1[3-9]|[2-9][0-9]|1[01][0-9]) years? old").find(normalized)?.groupValues?.get(1)
        if (age != null) return "demographic:age:$age"
        return normalized
    }

    private fun plausibleName(value: String): Boolean {
        val words = value.split(Regex("\\s+")).filter(String::isNotBlank)
        return words.size in 1..5 && value.length in 2..60 &&
            words.none { it.lowercase() in setOf("tired", "hungry", "fine", "good", "okay", "ok", "here", "ready", "crazy", "maybe", "later") }
    }
}
