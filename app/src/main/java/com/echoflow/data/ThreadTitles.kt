package com.echoflow.data

/**
 * A human title derived from the first user message, used when no model-generated title is
 * available (local/offline engines, or a failed title request).
 *
 * Deliberately does **not** append an ellipsis. The drawer row already clips overflow itself with a
 * single trailing ellipsis at the true right edge; baking "..." into the stored title only chopped
 * titles to four or five words and left every row half-empty. We keep a generous slice and let the
 * row decide what actually fits, guarding only against a pathologically long run with no spaces.
 */
fun fallbackThreadTitle(source: String, maxWords: Int = 10, maxChars: Int = 60): String {
    val collapsed = source.trim().replace("\\s+".toRegex(), " ")
    if (collapsed.isEmpty()) return ""
    val byWords = collapsed.split(" ").take(maxWords).joinToString(" ")
    return if (byWords.length > maxChars) byWords.take(maxChars).trimEnd() else byWords
}
