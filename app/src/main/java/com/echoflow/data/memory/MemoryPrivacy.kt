package com.echoflow.data.memory

/** Conservative defence in depth, not a guarantee that arbitrary secrets can be detected. */
object MemoryPrivacy {
    private val basicCredentials = Regex("(?i)\\bBasic\\s+([A-Za-z0-9+/]+={0,2})")
    private val patterns = listOf(
        Regex("-----BEGIN [^-]*PRIVATE KEY-----[\\s\\S]*?-----END [^-]*PRIVATE KEY-----"),
        Regex("\\b(?:sk-[A-Za-z0-9_-]{16,}|sm_[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]{12,}=*"),
        // Consume complete quoted values (including escapes/spaces) and short
        // nonempty bare values. Optional key quotes cover JSON credential fields.
        Regex("""(?im)["']?\b(?:password|api[_ -]?key|access[_ -]?token|client[_ -]?secret|auth[_ -]?token|token)["']?\s*[:=]\s*(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s"',;}]+)"""),
    )
    fun redact(text: String): String {
        // A bare "Basic" is also ordinary prose. HTTP Basic credentials decode
        // to a user:password pair; do not erase phrases such as "basic knowledge".
        val withoutBasic = basicCredentials.replace(text) { match ->
            val decoded = runCatching { java.util.Base64.getDecoder().decode(match.groupValues[1]) }.getOrNull()
            if (decoded?.contains(':'.code.toByte()) == true) "[credential redacted]" else match.value
        }
        return patterns.fold(withoutBasic) { value, pattern -> pattern.replace(value, "[credential redacted]") }
    }
}
