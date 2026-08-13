package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the design rule behind the freshness work: **one epistemic policy, many transports.**
 *
 * The app reaches the web four ways — OpenRouter server tools, client-side function calling, the
 * on-device `search:` protocol, and pre-injected results for custom providers. Each needs different
 * mechanics, but all four must answer "should I check this?" the same way, because the bug that
 * started this (a model confidently reporting the 2022 World Cup winner in 2026) is a policy bug,
 * not a plumbing one. Before these tests existed the four prompts had drifted into four different
 * philosophies, and the on-device one contained a rule that fired directly against the shared gate.
 *
 * These are string assertions, which are blunt — but nothing else in the codebase verifies prompt
 * content at all, and prompt regressions are silent: the app builds, ships, and quietly answers
 * from stale memory. A failure here means someone edited a prompt in a way that breaks the
 * invariant; fix the prompt, or update the constant if the wording genuinely moved on.
 */
class SystemPromptsFreshnessTest {

    private val date = "Thursday, August 13, 2026"

    /** Every prompt that can reach the web, paired with how it is expected to get there. */
    private fun searchCapablePrompts(): Map<String, String> = mapOf(
        "cloud + openrouter" to SystemPrompts.build(false, "openrouter", date),
        "cloud + exa" to SystemPrompts.build(false, "exa", date),
        "cloud + parallel" to SystemPrompts.build(false, "parallel", date),
        "cloud + firecrawl" to SystemPrompts.build(false, "firecrawl", date),
        "local + exa" to SystemPrompts.build(true, "exa", date),
        "local + parallel" to SystemPrompts.build(true, "parallel", date),
        "local + firecrawl" to SystemPrompts.build(true, "firecrawl", date),
        "custom provider + exa" to SystemPrompts.buildCustomProvider("exa", date),
        "custom provider + firecrawl" to SystemPrompts.buildCustomProvider("firecrawl", date),
        "adviser" to SystemPrompts.buildEchoAdviser("Sage", date),
        "agent" to SystemPrompts.buildEchoAgent("Scout", date),
    )

    private fun searchOffPrompts(): Map<String, String> = mapOf(
        "cloud, search off" to SystemPrompts.build(false, "off", date),
        "local, search off" to SystemPrompts.build(true, "off", date),
        "custom provider, search off" to SystemPrompts.buildCustomProvider("off", date),
    )

    private fun allConversationalPrompts(): Map<String, String> =
        searchCapablePrompts() + searchOffPrompts()

    @Test
    fun `every conversational prompt states the volatility test`() {
        allConversationalPrompts().forEach { (name, prompt) ->
            assertTrue(
                "$name is missing the freshness gate — it will answer stale questions from memory",
                prompt.contains("could the correct answer have changed since you were trained"),
            )
        }
    }

    @Test
    fun `every conversational prompt names the recurring-event trap`() {
        // The specific failure that started this: answers that look like settled history but are
        // replaced on a schedule. A prompt that drops this passes the generic rule and still fails
        // "who won the World Cup?".
        allConversationalPrompts().forEach { (name, prompt) ->
            assertTrue(
                "$name no longer warns about recurring events (tournaments, elections, awards)",
                prompt.contains("Recurring events are the trap"),
            )
        }
    }

    @Test
    fun `every conversational prompt operationalises the current date`() {
        allConversationalPrompts().forEach { (name, prompt) ->
            assertTrue("$name does not state today's date", prompt.contains(date))
            assertTrue(
                "$name states the date but never tells the model to act on it",
                prompt.contains("You cannot see your own training cutoff"),
            )
        }
    }

    @Test
    fun `the freshness gate is the last thing every prompt says`() {
        // It has to win the recency slot. Before this change the final section was the markdown
        // formatting contract, so "format nicely" was the freshest instruction in the model's head
        // at the moment it chose whether to search.
        allConversationalPrompts().forEach { (name, prompt) ->
            val gateStart = prompt.indexOf("## Before you answer")
            assertTrue("$name has no freshness gate section", gateStart >= 0)
            assertTrue(
                "$name puts the formatting contract after the freshness gate — the gate must land last",
                prompt.indexOf("## Formatting") < gateStart,
            )
        }
    }

    @Test
    fun `each transport gets a remedy it can actually perform`() {
        // Telling a model to call a tool it does not have produces a hallucinated function call
        // instead of an answer, so the remedy is parameterised per transport.
        assertTrue(
            "cloud tool-calling should be told to search",
            SystemPrompts.build(false, "exa", date).contains("search before answering"),
        )
        assertTrue(
            "on-device has no tool calling — it must be told to *request* a search",
            SystemPrompts.build(true, "exa", date).contains("request a search before answering"),
        )
        assertTrue(
            "custom providers get results pre-injected — they must be pointed at those",
            SystemPrompts.buildCustomProvider("exa", date)
                .contains("answer from the search results provided above"),
        )
        assertTrue(
            "with no search backend the only honest remedy is disclosure",
            SystemPrompts.build(false, "off", date)
                .contains("say plainly that your information may be out of date"),
        )
    }

    @Test
    fun `no search-off prompt tells the model to search`() {
        searchOffPrompts().forEach { (name, prompt) ->
            assertFalse(
                "$name promises a search the transport cannot perform",
                prompt.contains("search before answering"),
            )
        }
    }

    @Test
    fun `the on-device prompt does not restate the search policy`() {
        // The gate is the sole owner on-device: context is scarce, and a small model given two
        // phrasings of one decision oscillates. This is the rule that regressed before — the old
        // text said "do not search unless the user clearly asks for fresh information", which
        // fires against the gate on exactly the questions the gate exists to catch.
        val local = SystemPrompts.build(true, "exa", date)
        assertFalse(
            "the on-device prompt has regained a competing search-policy rule",
            local.contains("unless the user's latest message clearly asks"),
        )
        assertEquals(
            "the volatility rule must appear exactly once on-device",
            1,
            Regex("could the correct answer have changed since you were trained").findAll(local).count(),
        )
    }

    @Test
    fun `the on-device prompt keeps its protocol mechanics`() {
        // Thinning the policy must not thin the transport. Without these the model cannot search
        // at all, or leaks protocol tokens into the visible answer.
        val local = SystemPrompts.build(true, "exa", date)
        assertTrue("lost the search: line format", local.contains("search: concise search query"))
        assertTrue("lost the search budget", local.contains("Maximum 3 searches per answer"))
        assertTrue(
            "lost the guard against protocol tokens leaking into the answer",
            local.contains("never write search tags"),
        )
    }

    @Test
    fun `search-capable prompts tell the model to prefer newer sources over memory`() {
        // The other half of stale confidence: having searched, the model still has to not pick the
        // familiar-sounding 2022 result over the 2026 one.
        searchCapablePrompts()
            .filterKeys { it != "adviser" && it != "agent" }
            .forEach { (name, prompt) ->
                assertTrue(
                    "$name never says a fresh result beats memory",
                    prompt.contains("the result wins"),
                )
            }
    }

    @Test
    fun `no prompt keeps the old topic-bucket phrasing`() {
        // The original bug in one line: sorting questions by subject sent "who won the World Cup"
        // into the do-not-search bucket, because it is topically history.
        allConversationalPrompts().forEach { (name, prompt) ->
            assertFalse(
                "$name still classifies by topic instead of volatility",
                prompt.contains("Do not search for stable knowledge"),
            )
        }
    }

    @Test
    fun `the search budget is not the first thing said about searching`() {
        // Budget-before-permission taught the model that searching is scarce before it learned
        // when to do it. Same limit, stated as a resource, after the decision rule.
        listOf(
            "cloud + openrouter" to SystemPrompts.build(false, "openrouter", date),
            "cloud + exa" to SystemPrompts.build(false, "exa", date),
        ).forEach { (name, prompt) ->
            val budget = prompt.indexOf("budget of 3 searches")
            val whenToSearch = prompt.indexOf("Search")
            assertTrue("$name never states a search budget", budget >= 0)
            assertTrue(
                "$name leads with the limit instead of the permission",
                whenToSearch in 0 until budget,
            )
        }
    }

    @Test
    fun `the on-device prompt stays within its context budget`() {
        // Local models have small windows, so the gate's cost has to be paid for by thinning the
        // protocol section rather than added on top. This is a regression guard, not a target:
        // if a future edit needs the room, measure the win and move the number deliberately.
        val local = SystemPrompts.build(true, "exa", date)
        assertTrue(
            "the on-device prompt grew to ${local.length} chars; it must stay compact for small models",
            local.length < 4200,
        )
    }
}
