package com.echoflow.ui.chat

import com.echoflow.data.*
import com.echoflow.ui.ChatResponsePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Interprets search tags from local models before their output reaches the conversation UI. */
internal class LocalSearchProtocol(
    private val localLlmService: LocalLlmService,
    private val webSearchService: WebSearchService,
) {
    /** Thrown to abort collection of a local generation once a complete tag is parsed. */
    private class SearchTagFound : Exception()

    private enum class TagState { HOLDING, TEXT, TAG_XML, TAG_PLAIN }

    /**
     * Agentic search loop for on-device models. The system prompt instructs the model to
     * reply with a single `search: query` line when it needs the web; output is
     * held back until it's clear whether the reply is a tag or normal text, so partial
     * tags never reach the UI. Results are injected into the live session and generation
     * continues, up to [MAX_LOCAL_SEARCH_ROUNDS] rounds.
     */
    fun stream(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        provider: String,
        searchKey: String,
        params: InferenceParams
    ): Flow<StreamChunk> = flow {
        var round = 0
        var continuation = false

        while (true) {
            val allowTag = round < MAX_LOCAL_SEARCH_ROUNDS
            val upstream = if (continuation) {
                localLlmService.continueGeneration()
            } else {
                localLlmService.generate(model, chatId, history, systemPrompt, params)
            }

            val buf = StringBuilder()
            var state = if (allowTag) TagState.HOLDING else TagState.TEXT
            var emittedLen = 0

            try {
                upstream.collect { chunk ->
                    if (chunk !is StreamChunk.Content) {
                        emit(chunk)
                        return@collect
                    }
                    buf.append(chunk.text)
                    val trimmed = buf.toString().trimStart()

                    if (state == TagState.HOLDING) {
                        state = when {
                            trimmed.startsWith("<search>") -> TagState.TAG_XML
                            trimmed.lowercase().startsWith("search:") -> TagState.TAG_PLAIN
                            trimmed.isNotEmpty() &&
                                !"<search>".startsWith(trimmed.take(8)) &&
                                !"search:".startsWith(trimmed.lowercase().take(7)) -> TagState.TEXT
                            else -> TagState.HOLDING
                        }
                    }

                    when (state) {
                        TagState.TEXT -> {
                            val full = buf.toString()
                            if (emittedLen < full.length) {
                                emit(StreamChunk.Content(full.substring(emittedLen)))
                                emittedLen = full.length
                            }
                        }
                        TagState.TAG_XML -> if (trimmed.contains("</search>")) throw SearchTagFound()
                        TagState.TAG_PLAIN -> if (trimmed.contains("\n")) throw SearchTagFound()
                        TagState.HOLDING -> Unit
                    }
                }
            } catch (e: SearchTagFound) {
                // Expected: upstream cancelled, the tag is complete in buf.
            }

            val query = when (state) {
                TagState.TAG_XML, TagState.HOLDING, TagState.TAG_PLAIN -> extractSearchQuery(buf.toString())
                TagState.TEXT -> null
            }

            if (query == null) {
                // Normal answer (or an unparseable tag): flush anything still held back.
                if (state != TagState.TEXT) {
                    val leftover = buf.toString().trim()
                    if (leftover.isNotEmpty() && extractSearchQuery(leftover) == null) {
                        emit(StreamChunk.Content(leftover))
                    }
                }
                break
            }

            emit(StreamChunk.SearchStarted(query))
            val sources = try {
                webSearchService.search(provider, searchKey, query)
            } catch (e: Exception) {
                emit(StreamChunk.StatusNote("Search failed: ${e.message}"))
                emptyList()
            }
            emit(StreamChunk.SearchSources(query, sources))

            val resultBlock = if (sources.isEmpty()) {
                "The search failed or returned nothing. Answer from your own knowledge and " +
                    "tell the user you could not verify current information."
            } else {
                "Search results for \"$query\":\n" + formatSearchResultsForModel(sources)
            }
            round++
            val instruction = if (round >= MAX_LOCAL_SEARCH_ROUNDS) {
                "\n\nAnswer the user's question now using these results, citing claims as [n](url). " +
                    "Do not search again.\n\nEchoFlow reply:"
            } else {
                "\n\nAnswer the user's question now using these results, citing claims as [n](url). " +
                    "Only reply with another single-line search: query if these results are truly insufficient.\n\nEchoFlow reply:"
            }
            localLlmService.appendContext(resultBlock + instruction)
            continuation = true
        }
    }

    private fun extractSearchQuery(text: String): String? {
        return ChatResponsePolicy.extractSearchQuery(text)
    }

    private companion object {
        const val MAX_LOCAL_SEARCH_ROUNDS = 3
    }
}
