package dev.firecrawl.anydoc

/**
 * A conversion that could not produce useful Markdown.
 *
 * [code] is the stable name to branch on (same strings as the Rust crate,
 * Node `error.code`, and Python exception classes):
 *
 * - `unsupported` — unknown format, or one that cannot be converted
 * - `malformed` — structurally unusable
 * - `encrypted` — password-protected
 * - `resourceLimit` — decompression / nesting / size limit
 * - `missingPart` — a required package part is absent
 * - `io` — the bytes could not be read
 */
class ConvertException(
    val code: String,
    override val message: String,
) : RuntimeException(message)
