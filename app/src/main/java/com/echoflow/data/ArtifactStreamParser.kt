package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Splits a model's reply stream into prose (kept in the chat bubble) and an artifact body (routed
 * out to the workspace). The model is instructed (see [SystemPrompts.buildArtifact]) to wrap an
 * artifact in a sentinel block:
 *
 * ```
 * <echo:artifact type="html" title="Pricing page">
 * ...the full artifact...
 * </echo:artifact>
 * ```
 *
 * This is a streaming state machine: everything outside the block passes through as
 * [StreamChunk.Content]; everything inside is suppressed from the bubble and surfaced as
 * [StreamChunk.ArtifactStarted] / [ArtifactProgress] / [ArtifactCompleted]. Tags split across
 * chunk boundaries are handled with a holdback, and a stream that ends mid-artifact still emits a
 * (truncated) [ArtifactCompleted] so partial work is not lost.
 *
 * Non-content chunks (reasoning, search, …) pass through untouched.
 */
fun Flow<StreamChunk>.extractArtifacts(): Flow<StreamChunk> = flow {
    val parser = ArtifactStreamParser()
    collect { chunk -> parser.onChunk(chunk).forEach { emit(it) } }
    parser.onComplete().forEach { emit(it) }
}

class ArtifactStreamParser {

    private enum class State { OUTSIDE, INSIDE }

    private var state = State.OUTSIDE
    private var pending = StringBuilder()      // OUTSIDE text not yet decided
    private var artifactBuf = StringBuilder()  // INSIDE body accumulated so far
    private var startedEmitted = false
    private var title = ""
    private var type = Artifact.TYPE_HTML

    fun onChunk(chunk: StreamChunk): List<StreamChunk> {
        if (chunk !is StreamChunk.Content) return listOf(chunk)
        val out = mutableListOf<StreamChunk>()
        if (state == State.OUTSIDE) pending.append(chunk.text) else artifactBuf.append(chunk.text)
        drain(out)
        return out
    }

    fun onComplete(): List<StreamChunk> {
        val out = mutableListOf<StreamChunk>()
        when (state) {
            State.OUTSIDE -> {
                // Flush any held-back tail (it never became an open tag).
                if (pending.isNotEmpty()) {
                    out.add(StreamChunk.Content(pending.toString()))
                    pending.clear()
                }
            }
            State.INSIDE -> {
                // Stream ended without a closing tag — surface what we have, marked truncated.
                out.add(
                    StreamChunk.ArtifactCompleted(
                        title = title,
                        artifactType = type,
                        content = artifactBuf.toString().trim(),
                        truncated = true,
                    )
                )
                reset()
            }
        }
        return out
    }

    /** Run the state machine over whatever is currently buffered, appending emissions to [out]. */
    private fun drain(out: MutableList<StreamChunk>) {
        var progressed = true
        while (progressed) {
            progressed = false
            if (state == State.OUTSIDE) {
                val text = pending
                val openAt = text.indexOf(OPEN_PREFIX)
                if (openAt >= 0) {
                    // Need the whole opening tag (up to '>') before we can start the artifact.
                    val gt = text.indexOf('>', openAt)
                    if (gt < 0) {
                        // Open tag still arriving — emit the prose before it, keep the rest.
                        if (openAt > 0) {
                            out.add(StreamChunk.Content(text.substring(0, openAt)))
                            pending = StringBuilder(text.substring(openAt))
                        }
                        return
                    }
                    // Emit prose before the tag.
                    if (openAt > 0) out.add(StreamChunk.Content(text.substring(0, openAt)))
                    val openTag = text.substring(openAt, gt + 1)
                    title = attr(openTag, "title")
                    type = Artifact.normalizeType(attr(openTag, "type"))
                    if (!startedEmitted) {
                        out.add(StreamChunk.ArtifactStarted(title, type))
                        startedEmitted = true
                    }
                    state = State.INSIDE
                    artifactBuf = StringBuilder(text.substring(gt + 1))
                    pending = StringBuilder()
                    progressed = true
                } else {
                    // No open tag: emit everything except a tail that could be a partial open tag.
                    val keep = partialSuffixLen(text, OPEN_PREFIX)
                    val emitLen = text.length - keep
                    if (emitLen > 0) {
                        out.add(StreamChunk.Content(text.substring(0, emitLen)))
                        pending = StringBuilder(text.substring(emitLen))
                    }
                    return
                }
            } else { // INSIDE
                val body = artifactBuf
                val closeAt = body.indexOf(CLOSE_TAG)
                if (closeAt >= 0) {
                    val content = body.substring(0, closeAt).trim()
                    out.add(
                        StreamChunk.ArtifactCompleted(
                            title = title,
                            artifactType = type,
                            content = content,
                        )
                    )
                    val rest = body.substring(closeAt + CLOSE_TAG.length)
                    reset()
                    pending = StringBuilder(rest)
                    progressed = rest.isNotEmpty()
                } else {
                    // No close yet — report progress, but never let a partial close tag count.
                    val keep = partialSuffixLen(body, CLOSE_TAG)
                    out.add(StreamChunk.ArtifactProgress(body.length - keep))
                    return
                }
            }
        }
    }

    private fun reset() {
        state = State.OUTSIDE
        artifactBuf = StringBuilder()
        startedEmitted = false
        title = ""
        type = Artifact.TYPE_HTML
    }

    companion object {
        private const val OPEN_PREFIX = "<echo:artifact"
        private const val CLOSE_TAG = "</echo:artifact>"

        private val ATTR_RE = Regex("""(\w+)\s*=\s*"([^"]*)"""")

        private fun attr(tag: String, name: String): String =
            ATTR_RE.findAll(tag).firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
                ?.groupValues?.get(2)?.trim().orEmpty()

        /**
         * Length of the longest suffix of [text] that is a (proper) prefix of [marker]. Used to
         * hold back the tail of a buffer that might be the beginning of a tag split across chunks.
         */
        private fun partialSuffixLen(text: CharSequence, marker: String): Int {
            val max = minOf(text.length, marker.length - 1)
            for (len in max downTo 1) {
                var match = true
                for (i in 0 until len) {
                    if (text[text.length - len + i] != marker[i]) { match = false; break }
                }
                if (match) return len
            }
            return 0
        }
    }
}
