package com.echoflow.data

/** One decoded server-sent event: the `event:` name (absent on unnamed streams) and its `data:` body. */
internal data class SseEvent(val name: String?, val data: String)

/**
 * Line-at-a-time server-sent event decoder.
 *
 * Split out of the network loop so the framing can be tested without a live provider. The Deep
 * Research feeds it serves — Parallel's task run events and Exa's agent run stream — can't be
 * exercised without paid API keys, so the part that *is* deterministic is worth pinning down.
 *
 * Follows the SSE wire format: `event:` names a frame, `data:` lines accumulate (multiple `data:`
 * lines in one frame join with newlines, which is how JSON payloads may be split), a blank line
 * dispatches, and lines starting with `:` are comments/keep-alives. Unlike the chat transport's
 * decoder this keeps the event name, because both providers put the meaning there rather than in
 * the payload.
 */
internal class SseDecoder {
    private var name: String? = null
    private val data = StringBuilder()

    /** Feed one line. Returns a frame when that line completed one, else null. */
    fun accept(line: String): SseEvent? = when {
        line.startsWith(":") -> null // comment / keep-alive
        line.startsWith("event:") -> {
            name = line.substring(6).trim()
            null
        }
        line.startsWith("data:") -> {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.substring(5).trim())
            null
        }
        line.isBlank() -> flush()
        else -> null // id:, retry:, and anything else we don't act on
    }

    /** Emit a frame left buffered by a stream that ended without a trailing blank line. */
    fun flush(): SseEvent? {
        if (data.isEmpty()) {
            name = null
            return null
        }
        val frame = SseEvent(name, data.toString())
        name = null
        data.clear()
        return frame
    }
}
