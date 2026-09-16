package com.echoflow.data.memory

/** Conservative defence in depth, not a guarantee that arbitrary secrets can be detected. */
object MemoryPrivacy {
    private val patterns = listOf(
        Regex("-----BEGIN [^-]*PRIVATE KEY-----[\\s\\S]*?-----END [^-]*PRIVATE KEY-----"),
        Regex("\\b(?:sk-[A-Za-z0-9_-]{16,}|sm_[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{12,}=*"),
        Regex("(?im)\\b(?:password|api[_ -]?key|access[_ -]?token|client[_ -]?secret)\\s*[:=]\\s*[\"']?[^\\s\"']{8,}"),
    )
    fun redact(text: String): String = patterns.fold(text) { value, pattern -> pattern.replace(value, "[credential redacted]") }
}
