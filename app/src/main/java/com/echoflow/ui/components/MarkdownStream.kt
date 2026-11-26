package com.echoflow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Streaming plan for one reply. A reply is split into top-level blocks at blank lines, and runs of
 * finished blocks are grouped into chunks of about [DEFAULT_CHUNK_TARGET] characters. A chunk is
 * frozen once a later block has started: its text never changes again, so its composable is
 * skipped and its native view is never re-parsed. Only the open chunk at the end changes while
 * tokens arrive. Chunk boundaries depend only on the text before them, so a finished reply plans
 * exactly as it did while it streamed, and the layout does not move when streaming ends.
 *
 * A top-level fenced block whose info string names a registered [MarkdownBlockExtension] becomes
 * its own [MarkdownExtensionBlock] instead of markdown. That is the seam for block types that are
 * not markdown, such as generative UI specs streamed inside a reply.
 */
@Immutable
internal sealed interface MarkdownStreamBlock {
    /** Source offset where the block starts. Stable for the life of a stream. */
    val key: Int
}

@Immutable
internal data class MarkdownChunk(override val key: Int, val source: String) : MarkdownStreamBlock

/** A fenced block drawn by the extension registered for [kind], the first word of its info string. */
@Immutable
data class MarkdownExtensionBlock(
    val key: Int,
    val kind: String,
    val info: String,
    val body: String,
    val complete: Boolean,
)

@Immutable
internal data class MarkdownExtensionPart(val block: MarkdownExtensionBlock) : MarkdownStreamBlock {
    override val key: Int get() = block.key
}

/** Draws one kind of fenced block. [MarkdownExtensionBlock.body] grows while the reply streams. */
interface MarkdownBlockExtension {
    @Composable
    fun Render(block: MarkdownExtensionBlock, modifier: Modifier)
}

/** Fenced-block extensions by kind. Empty by default, so every fence draws as code. */
val LocalMarkdownBlockExtensions = staticCompositionLocalOf<Map<String, MarkdownBlockExtension>> { emptyMap() }

/**
 * The position of the extension block being drawn among the reply's blocks of the same kind,
 * counting only the ones whose info string is exactly the kind (not `weave edit …`). Lets an
 * extension cap how many blocks a reply renders (Echo UI, 05 §11.4).
 */
val LocalMarkdownExtensionOrdinal = androidx.compose.runtime.compositionLocalOf { 0 }

@Immutable
internal class MarkdownStreamPlan(val blocks: List<MarkdownStreamBlock>, val references: String)

internal const val DEFAULT_CHUNK_TARGET = 1_200

/**
 * Plans a growing text incrementally: frozen chunks are kept, and only the text after the last
 * frozen chunk is scanned again. Any text that does not extend the frozen prefix starts over.
 */
internal class MarkdownStreamPlanner(
    private val extensionKinds: Set<String> = emptySet(),
    private val chunkTarget: Int = DEFAULT_CHUNK_TARGET,
) {
    private val frozen = ArrayList<MarkdownStreamBlock>()
    private var frozenText = ""
    private var frozenEnd = 0

    fun plan(text: String): MarkdownStreamPlan {
        if (frozenEnd > text.length || !text.regionMatches(0, frozenText, 0, frozenEnd)) {
            frozen.clear()
            frozenEnd = 0
        }
        frozenText = text
        val raw = scanTopLevelBlocks(text, frozenEnd)
        val live = ArrayList<MarkdownStreamBlock>()
        var chunkStart = -1
        var chunkEnd = -1
        fun freeze(block: MarkdownStreamBlock, end: Int) {
            frozen += block
            frozenEnd = end
        }
        fun chunk(start: Int, end: Int) = MarkdownChunk(start, text.substring(start, end).trimEnd())
        for ((index, block) in raw.withIndex()) {
            val settled = index < raw.lastIndex
            val kind = block.extensionKind(extensionKinds)
            if (kind != null) {
                if (chunkStart >= 0) {
                    freeze(chunk(chunkStart, chunkEnd), chunkEnd)
                    chunkStart = -1
                }
                val part = MarkdownExtensionPart(
                    MarkdownExtensionBlock(
                        key = block.start,
                        kind = kind,
                        info = block.fence!!.info,
                        body = text.substring(minOf(block.bodyStart, block.bodyEnd), block.bodyEnd).removeSuffix("\n"),
                        complete = block.fenceClosed,
                    )
                )
                if (settled) freeze(part, block.end) else live += part
                continue
            }
            if (chunkStart < 0) chunkStart = block.start
            chunkEnd = block.end
            if (settled && chunkEnd - chunkStart >= chunkTarget) {
                freeze(chunk(chunkStart, chunkEnd), chunkEnd)
                chunkStart = -1
            }
        }
        if (chunkStart >= 0) live += chunk(chunkStart, chunkEnd)
        return MarkdownStreamPlan(frozen + live.filterNot { it is MarkdownChunk && it.source.isEmpty() }, referenceDefinitions(text))
    }
}

private class RawBlock(val start: Int, val fence: MarkdownFence?, val fenceInfoComplete: Boolean, val bodyStart: Int) {
    var end = start
    var bodyEnd = -1
    var fenceClosed = false

    fun extensionKind(kinds: Set<String>): String? {
        if (fence == null || !fenceInfoComplete || kinds.isEmpty()) return null
        return fence.info.substringBefore(' ').takeIf { it in kinds }
    }
}

/**
 * Splits [text] from [from] into top-level blocks. A block ends at a blank line followed by a line
 * that starts a new top-level block; blank lines inside fences and display math never split, and
 * a list continues across blank lines. A boundary is only taken once it can no longer change: an
 * unfinished last line that could still become a list item does not end a list.
 */
private fun scanTopLevelBlocks(text: String, from: Int): List<RawBlock> {
    val blocks = ArrayList<RawBlock>()
    var current: RawBlock? = null
    var fence: MarkdownFence? = null
    var mathCloser: String? = null
    var pendingBlank = false
    var inList = false
    var previousBlank = true
    var lineStart = from
    while (lineStart < text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val terminated = lineEnd < text.length
        val line = text.substring(lineStart, lineEnd)
        val next = lineEnd + 1
        val openFence = fence
        if (openFence != null) {
            if (closesFence(line, openFence)) {
                fence = null
                val block = current
                if (block != null && block.fence === openFence) {
                    block.fenceClosed = true
                    block.bodyEnd = lineStart
                    pendingBlank = true
                }
            }
            lineStart = next
            continue
        }
        val closer = mathCloser
        if (closer != null) {
            if (line.contains(closer)) mathCloser = null
            lineStart = next
            continue
        }
        if (line.isBlank()) {
            pendingBlank = current != null
            previousBlank = true
            lineStart = next
            continue
        }
        if (current == null || (pendingBlank && startsTopLevelBlock(line, terminated, inList))) {
            current?.end = lineStart
            val blockFence = openingFence(line)
            current = RawBlock(lineStart, blockFence, terminated, minOf(next, text.length))
            blocks += current
            inList = false
        }
        pendingBlank = false
        inList = when {
            LIST_ITEM.containsMatchIn(line) -> true
            line.leadingIndentColumns() > 0 -> inList
            else -> inList && !previousBlank
        }
        previousBlank = false
        val lineFence = openingFence(line, maxIndent = Int.MAX_VALUE)
        if (lineFence != null) {
            fence = if (current.fence != null && current.start == lineStart) current.fence else lineFence
        } else {
            mathCloser = displayMathCloser(line)
        }
        lineStart = next
    }
    current?.end = text.length
    blocks.forEach { if (it.bodyEnd < 0) it.bodyEnd = it.end }
    return blocks
}

private val LIST_ITEM = Regex("""^ {0,3}(?:[*+-]|\d{1,9}[.)])(?:[ \t]|$)""")
private val LIST_MARKER_PREFIX = Regex("""^(?:[*+-]|\d{1,9}[.)]?)$""")

private fun startsTopLevelBlock(line: String, terminated: Boolean, inList: Boolean): Boolean {
    if (!inList) return true
    if (line.leadingIndentColumns() > 0 || LIST_ITEM.containsMatchIn(line)) return false
    return terminated || !LIST_MARKER_PREFIX.matches(line)
}

private fun displayMathCloser(line: String): String? {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("$$") -> "$$".takeUnless { trimmed.indexOf("$$", 2) >= 0 }
        trimmed.startsWith("\\[") -> "\\]".takeUnless { trimmed.indexOf("\\]", 2) >= 0 }
        else -> null
    }
}

private val REFERENCE_DEFINITION = Regex("""(?m)^ {0,3}\[(?!\^)[^\]\n]+\]:[ \t]*\S.*$""")

/** Link reference definitions anywhere in the reply outside code, so a reference resolves in every chunk. */
internal fun referenceDefinitions(text: String): String {
    if (!text.contains("]:")) return ""
    val code = markdownCodeMask(text)
    return REFERENCE_DEFINITION.findAll(text)
        .filterNot { code[it.range.first] }
        .joinToString("\n") { it.value.trim() }
}

/** Markdown handed to the native renderer for one chunk. */
internal fun prepareMarkdownChunk(source: String, references: String, streamingTail: Boolean): String {
    val html = normalizeInlineHtml(neutralizeUnsupportedVideo(source))
    val body = if (streamingTail) repairStreamingMarkdown(html) else normalizeMath(html)
    if (references.isEmpty()) return body
    // Shared definitions must land outside a fence the chunk leaves open, or they draw as code. A
    // quoted fence already ends with its quote at the blank line; a top-level one needs a closer.
    val open = unclosedFence(body)
    val closed = if (open == null || open.quoteDepth > 0) body else body + "\n" + open.fence.marker.toString().repeat(open.fence.length)
    return "$closed\n\n$references"
}
