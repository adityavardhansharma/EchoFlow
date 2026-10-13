package com.echoflow.data

import com.squareup.moshi.Moshi
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
 * Not every model honours the sentinel, so until a reply has produced an artifact two fallbacks
 * apply: a fenced html / markdown / latex code block is taken as the artifact, and when the stream
 * ends with none, a page the model sent as a tool call (reported by [StreamChunk.TurnEnded]) is
 * recovered. If even that finds nothing, a short note says why the reply has no artifact.
 *
 * Other non-content chunks (reasoning, search, …) pass through untouched.
 */
fun Flow<StreamChunk>.extractArtifacts(): Flow<StreamChunk> = flow {
    val parser = ArtifactStreamParser()
    collect { chunk -> parser.onChunk(chunk).forEach { emit(it) } }
    parser.onComplete().forEach { emit(it) }
}

class ArtifactStreamParser {

    // OUTSIDE: prose. CODE: inside a fenced block that is not an artifact (passes through).
    // INSIDE: inside an artifact, closed by [closeMarker].
    private enum class State { OUTSIDE, CODE, INSIDE }

    private var state = State.OUTSIDE
    private var pending = StringBuilder()      // OUTSIDE/CODE text not yet decided
    private var artifactBuf = StringBuilder()  // INSIDE body accumulated so far
    private var startedEmitted = false
    private var title = ""
    private var type = Artifact.TYPE_HTML
    private var closeMarker = CLOSE_TAG
    private var fenceMarker: String? = null     // INSIDE a fenced artifact, or CODE: the opener's run
    private var sawArtifact = false            // any artifact started in this reply
    private var lastTurn: StreamChunk.TurnEnded? = null
    private var ending = false                 // stream is over: an unterminated fence line counts
    private var codeOpener: String? = null     // CODE: opening fence line, held until the body shows it is not a sentinel
    private var codeAtLineStart = true         // CODE: [pending] starts at the beginning of a line
    private var swallowCloseFence: String? = null // a sentinel came wrapped in a fence: drop that fence's close line

    fun onChunk(chunk: StreamChunk): List<StreamChunk> {
        if (chunk is StreamChunk.TurnEnded) {
            // Diagnostic only; the last one decides the fallback in [onComplete].
            lastTurn = chunk
            return emptyList()
        }
        if (chunk !is StreamChunk.Content) return listOf(chunk)
        val out = mutableListOf<StreamChunk>()
        if (state == State.INSIDE) artifactBuf.append(chunk.text) else pending.append(chunk.text)
        drain(out)
        return out
    }

    fun onComplete(): List<StreamChunk> {
        val out = mutableListOf<StreamChunk>()
        // A closing fence may be the last line with no newline after it: end the line so it counts.
        ending = true
        drain(out)
        when (state) {
            State.OUTSIDE, State.CODE -> {
                // Flush any held-back tail (it never became an open tag).
                if (pending.isNotEmpty()) {
                    out.add(StreamChunk.Content(pending.toString()))
                    pending.clear()
                }
            }
            State.INSIDE -> {
                // Stream ended without a closing tag — surface what we have, marked truncated.
                out.add(completed(artifactBuf.toString(), truncated = true))
                reset()
            }
        }
        val turn = lastTurn
        if (!sawArtifact) {
            // Only some transports report how the turn ended; without it there is nothing to salvage.
            val salvaged = turn?.toolCalls?.firstNotNullOfOrNull { artifactFromToolCall(it) }
            if (salvaged != null) {
                sawArtifact = true
                out.add(StreamChunk.ArtifactStarted(salvaged.title, salvaged.artifactType))
                out.add(salvaged)
            } else {
                out.add(StreamChunk.Content(missingArtifactNote(turn)))
            }
        }
        return out
    }

    /** Run the state machine over whatever is currently buffered, appending emissions to [out]. */
    private fun drain(out: MutableList<StreamChunk>) {
        var progressed = true
        while (progressed) {
            progressed = false
            when (state) {
                State.OUTSIDE -> progressed = drainOutside(out)
                State.CODE -> progressed = drainCode(out)
                State.INSIDE -> progressed = drainInside(out)
            }
        }
    }

    private fun drainOutside(out: MutableList<StreamChunk>): Boolean {
        swallowCloseFence?.takeIf { sawArtifact }?.let { marker ->
            // Once the wrapped sentinel block is done, drop the fence line that closed around it.
            val lead = pending.indexOfFirst { !it.isWhitespace() }
            if (lead < 0) return false.also { if (ending) swallowCloseFence = null }
            val nl = pending.indexOf('\n', lead)
            val line = if (nl < 0) pending.substring(lead) else pending.substring(lead, nl)
            if (nl < 0 && !ending && marker.startsWith(line)) return false
            swallowCloseFence = null
            if (line.trimEnd().let { it.length >= marker.length && it.all { c -> c == marker[0] } }) {
                pending = StringBuilder(if (nl < 0) "" else pending.substring(nl))
                return pending.isNotEmpty()
            }
        }
        val text = pending
        val tagAt = text.indexOf(OPEN_PREFIX)
        // Fences only matter until the reply has its artifact; after that they are plain prose.
        val fenceAt = if (sawArtifact) -1 else FENCE_OPENERS.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        val useFence = fenceAt >= 0 && (tagAt < 0 || fenceAt < tagAt)

        if (useFence) {
            // Need the whole info string (up to the newline) to know the fence's language.
            val nl = text.indexOf('\n', fenceAt)
            if (nl < 0) {
                emitBefore(fenceAt, out)
                return false
            }
            // The opener's character and run length decide which line can close it.
            val fenceChar = text[fenceAt]
            var runEnd = fenceAt
            while (runEnd < nl && text[runEnd] == fenceChar) runEnd++
            val marker = fenceChar.toString().repeat(runEnd - fenceAt)
            val lang = text.substring(runEnd, nl).trim().substringBefore(' ').lowercase()
            if (lang in FENCE_ARTIFACT_LANGS) {
                emitBefore(fenceAt, out)
                startArtifact(title = "", rawType = lang, closeMarker = marker, out = out)
                fenceMarker = marker
                artifactBuf = StringBuilder(text.substring(nl + 1))
                pending = StringBuilder()
            } else {
                // Another code block: skip to its closing fence. Its opening line waits until the
                // body shows whether it only wraps a sentinel block.
                emitBefore(fenceAt, out)
                codeOpener = text.substring(fenceAt, nl + 1)
                pending = StringBuilder(text.substring(nl + 1))
                fenceMarker = marker
                codeAtLineStart = true
                state = State.CODE
            }
            return true
        }

        if (tagAt >= 0) {
            // Need the whole opening tag (up to '>') before we can start the artifact.
            val gt = text.indexOf('>', tagAt)
            if (gt < 0) {
                // Open tag still arriving — emit the prose before it, keep the rest.
                emitBefore(tagAt, out)
                return false
            }
            // Emit prose before the tag.
            if (tagAt > 0) out.add(StreamChunk.Content(text.substring(0, tagAt)))
            val openTag = text.substring(tagAt, gt + 1)
            startArtifact(attr(openTag, "title"), attr(openTag, "type"), CLOSE_TAG, out)
            artifactBuf = StringBuilder(text.substring(gt + 1))
            pending = StringBuilder()
            return true
        }

        // No marker: emit everything except a tail that could be a partial marker.
        val keep = maxOf(
            partialSuffixLen(text, OPEN_PREFIX),
            if (sawArtifact) 0 else FENCE_OPENERS.maxOf { partialSuffixLen(text, it) },
        )
        val emitLen = text.length - keep
        if (emitLen > 0) {
            out.add(StreamChunk.Content(text.substring(0, emitLen)))
            pending = StringBuilder(text.substring(emitLen))
        }
        return false
    }

    private fun drainCode(out: MutableList<StreamChunk>): Boolean {
        val marker = fenceMarker ?: FENCE_OPENERS.first()
        codeOpener?.let { opener ->
            val lead = pending.trimStart().toString()
            if (!sawArtifact && lead.startsWith(OPEN_PREFIX)) {
                // The fence only wraps a sentinel block: parse the block, drop both fence lines.
                codeOpener = null
                fenceMarker = null
                swallowCloseFence = marker
                state = State.OUTSIDE
                return true
            }
            if (!sawArtifact && !ending && OPEN_PREFIX.startsWith(lead)) return false
            out.add(StreamChunk.Content(opener))
            codeOpener = null
        }
        val text = pending
        val close = findFenceClose(text, marker, codeAtLineStart)
        if (close != null && (close.lineEnd >= 0 || ending)) {
            val end = if (close.lineEnd >= 0) close.lineEnd else text.length
            out.add(StreamChunk.Content(text.substring(0, end)))
            pending = StringBuilder(text.substring(end))
            fenceMarker = null
            state = State.OUTSIDE
            return true
        }
        // Hold back a line that may still turn into the closing fence.
        val emitLen = close?.fenceStart ?: undecidedTailStart(text, marker[0])
        if (emitLen > 0) {
            out.add(StreamChunk.Content(text.substring(0, emitLen)))
            pending = StringBuilder(text.substring(emitLen))
            codeAtLineStart = text[emitLen - 1] == '\n'
        }
        return false
    }

    private fun drainInside(out: MutableList<StreamChunk>): Boolean {
        val body = artifactBuf
        val marker = fenceMarker
        if (marker != null) {
            // The body starts right after the opening fence's newline and is never trimmed.
            val close = findFenceClose(body, marker, startsAtLine = true)
            if (close != null && (close.lineEnd >= 0 || ending)) {
                out.add(completed(body.substring(0, close.contentEnd), truncated = false))
                val rest = if (close.lineEnd >= 0) body.substring(close.lineEnd) else ""
                reset()
                pending = StringBuilder(rest)
                return rest.isNotEmpty()
            }
            out.add(StreamChunk.ArtifactProgress(close?.contentEnd ?: undecidedTailStart(body, marker[0])))
            return false
        }
        val closeAt = body.indexOf(closeMarker)
        if (closeAt >= 0) {
            out.add(completed(body.substring(0, closeAt), truncated = false))
            val rest = body.substring(closeAt + closeMarker.length)
            reset()
            pending = StringBuilder(rest)
            return rest.isNotEmpty()
        }
        // No close yet — report progress, but never let a partial close tag count.
        val keep = partialSuffixLen(body, closeMarker)
        out.add(StreamChunk.ArtifactProgress(body.length - keep))
        return false
    }

    private fun emitBefore(index: Int, out: MutableList<StreamChunk>) {
        if (index > 0) {
            out.add(StreamChunk.Content(pending.substring(0, index)))
            pending = StringBuilder(pending.substring(index))
        }
    }

    private fun startArtifact(title: String, rawType: String, closeMarker: String, out: MutableList<StreamChunk>) {
        this.title = title
        this.type = Artifact.normalizeType(rawType)
        this.closeMarker = closeMarker
        if (!startedEmitted) {
            out.add(StreamChunk.ArtifactStarted(this.title, this.type))
            startedEmitted = true
        }
        sawArtifact = true
        state = State.INSIDE
    }

    /** Builds the completed chunk, unwrapping a sentinel block a model put inside a code fence. */
    private fun completed(raw: String, truncated: Boolean): StreamChunk.ArtifactCompleted {
        val unwrapped = unwrapSentinel(raw)
        return StreamChunk.ArtifactCompleted(
            title = unwrapped?.title?.ifBlank { null } ?: title,
            artifactType = unwrapped?.artifactType ?: type,
            content = (unwrapped?.content ?: raw).trim(),
            truncated = truncated,
        )
    }

    private fun reset() {
        state = State.OUTSIDE
        artifactBuf = StringBuilder()
        startedEmitted = false
        title = ""
        type = Artifact.TYPE_HTML
        closeMarker = CLOSE_TAG
        fenceMarker = null
        codeOpener = null
    }

    /** A closing fence line: body ends at [contentEnd], the fence starts at [fenceStart], and its
     *  line ends at [lineEnd] (the newline's index), or -1 while the line is still arriving. */
    private class FenceClose(val contentEnd: Int, val fenceStart: Int, val lineEnd: Int)

    companion object {
        private const val OPEN_PREFIX = "<echo:artifact"
        private const val CLOSE_TAG = "</echo:artifact>"
        private val FENCE_OPENERS = listOf("```", "~~~")
        private val FENCE_ARTIFACT_LANGS = setOf("html", "htm", "markdown", "md", "latex", "tex")
        private val ARTIFACT_BODY_KEYS = listOf("content", "body", "html", "markdown", "latex", "code", "text")
        private val SEARCH_TOOL_NAMES = listOf("web_search", "web_fetch", "search", "fetch")

        private val ATTR_RE = Regex("""(\w+)\s*=\s*"([^"]*)"""")
        private val dynamicAdapter = Moshi.Builder().build().adapter(Any::class.java)

        private fun attr(tag: String, name: String): String =
            ATTR_RE.findAll(tag).firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
                ?.groupValues?.get(2)?.trim().orEmpty()

        /** If [raw] holds an `<echo:artifact>` block, returns its body with the tag's title/type. */
        private fun unwrapSentinel(raw: String): StreamChunk.ArtifactCompleted? {
            val open = raw.indexOf(OPEN_PREFIX)
            if (open < 0) return null
            val gt = raw.indexOf('>', open)
            if (gt < 0) return null
            val tag = raw.substring(open, gt + 1)
            val body = raw.substring(gt + 1).substringBefore(CLOSE_TAG)
            return StreamChunk.ArtifactCompleted(
                title = attr(tag, "title"),
                artifactType = Artifact.normalizeType(attr(tag, "type")),
                content = body.trim(),
            )
        }

        /**
         * Finds the line that closes a fence opened with [marker]: at a line start, the same
         * character at least as many times, then only whitespace (CommonMark's rule). A shorter
         * or info-bearing fence inside the block is content, not the close.
         */
        private fun findFenceClose(buf: CharSequence, marker: String, startsAtLine: Boolean): FenceClose? {
            val fenceChar = marker[0]
            var from = 0
            while (true) {
                val p = buf.indexOf(marker, from)
                if (p < 0) return null
                from = p + 1
                // At most three spaces of indent, and a real line start before them.
                var i = p - 1
                while (i >= 0 && buf[i] == ' ') i--
                val atLineStart = if (i < 0) startsAtLine else buf[i] == '\n'
                if (!atLineStart || p - 1 - i > 3) continue
                var q = p + marker.length
                while (q < buf.length && buf[q] == fenceChar) q++
                val nl = buf.indexOf('\n', q)
                val tail = if (nl < 0) buf.substring(q) else buf.substring(q, nl)
                if (tail.isNotBlank()) continue
                val lineStart = i + 1
                return FenceClose(contentEnd = maxOf(lineStart - 1, 0), fenceStart = lineStart, lineEnd = nl)
            }
        }

        /** Start of a trailing partial line that could still become a fence (else the length). */
        private fun undecidedTailStart(buf: CharSequence, fenceChar: Char): Int {
            val lineStart = buf.lastIndexOf('\n') + 1
            val tail = buf.substring(lineStart)
            val indent = tail.length - tail.trimStart(' ').length
            val undecided = indent <= 3 && tail.substring(indent).all { it == fenceChar }
            return if (undecided) lineStart else buf.length
        }

        private fun looksLikeArtifact(text: String): Boolean {
            val lower = text.lowercase()
            return OPEN_PREFIX in lower || "<!doctype" in lower || "<html" in lower || "\\documentclass" in lower
        }

        /**
         * Some models turn the sentinel block into a tool call instead of text. Recover the page
         * from the call's arguments: JSON when it parses, otherwise the raw argument string.
         */
        internal fun artifactFromToolCall(call: StreamChunk.ToolCallSummary): StreamChunk.ArtifactCompleted? {
            val name = call.name.lowercase()
            if (SEARCH_TOOL_NAMES.any { it in name }) return null
            val parsed = runCatching { dynamicAdapter.fromJson(call.arguments) }.getOrNull()
            val map = parsed as? Map<*, *>
            // An artifact-named call declares its body; take it as is, markers or not.
            val declared = if (map != null && "artifact" in name) {
                ARTIFACT_BODY_KEYS.firstNotNullOfOrNull { key -> (map[key] as? String)?.takeIf { it.isNotBlank() } }
            } else null
            val candidate: String? = if (declared != null) {
                declared
            } else if (parsed != null) {
                val strings = mutableListOf<String>()
                collectStrings(parsed, strings)
                strings.filter { looksLikeArtifact(it) }.maxByOrNull { it.length }
            } else {
                call.arguments.takeIf { looksLikeArtifact(it) }
            }
            val body = candidate ?: return null

            unwrapSentinel(body)?.let { inner ->
                return inner.copy(title = inner.title.ifBlank { (map?.get("title") as? String)?.trim().orEmpty() })
            }
            val rawType = (map?.get("type") as? String)
                ?: if ("\\documentclass" in body) Artifact.TYPE_LATEX else Artifact.TYPE_HTML
            return StreamChunk.ArtifactCompleted(
                // Blank is fine: ArtifactManager keeps the existing title or picks a default.
                title = (map?.get("title") as? String)?.trim().orEmpty(),
                artifactType = Artifact.normalizeType(rawType),
                content = body.trim(),
            )
        }

        private fun collectStrings(node: Any?, into: MutableList<String>) {
            when (node) {
                is String -> into.add(node)
                is Map<*, *> -> node.values.forEach { collectStrings(it, into) }
                is List<*> -> node.forEach { collectStrings(it, into) }
            }
        }

        /** Plain-language reason a reply came back without an artifact, from how the turn ended. */
        internal fun missingArtifactNote(turn: StreamChunk.TurnEnded?): String {
            val toolNames = turn?.toolCalls.orEmpty().map { it.name }
                .filter { name -> SEARCH_TOOL_NAMES.none { it in name.lowercase() } }
            val reason = when {
                toolNames.isNotEmpty() -> "it called a tool (${toolNames.joinToString()}) instead of writing the artifact"
                turn?.finishReason == "length" -> "it hit the output token limit"
                turn?.finishReason == "content_filter" -> "a content filter stopped it"
                turn?.finishReason.isNullOrBlank() || turn?.finishReason == "stop" -> "it finished without writing the artifact block"
                else -> "it ended with \"${turn?.finishReason}\""
            }
            return "\n\n_No artifact came back: $reason. Try again, or pick another model for artifacts._"
        }

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
