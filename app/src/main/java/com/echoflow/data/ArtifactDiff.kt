package com.echoflow.data

/**
 * Line-level change accounting between two artifact versions — the `+a −r` a version chip shows.
 *
 * These counts are glanceable badges, not a rendered diff: they answer "how big was this edit?"
 * so a stack of `v1 · v2 · v3` versions stops being opaque. Blank lines are ignored so the number
 * reflects substantive change, not reflow.
 */
data class LineDelta(val added: Int, val removed: Int) {
    val isEmpty: Boolean get() = added == 0 && removed == 0
}

/** A version paired with its delta from the prior version and its own size, for the chip row. */
data class VersionDelta(
    val version: Int,
    val delta: LineDelta,
    val lineCount: Int,
    /** True for the origin version (no predecessor): the chip shows a size, not a `+/−`. */
    val isFirst: Boolean,
)

/**
 * Non-blank lines from [text]. Blank lines are dropped so a diff count tracks real content, and
 * so whitespace-only reflow between versions doesn't inflate the badge.
 */
private fun significantLines(text: String?): List<String> =
    text?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: emptyList()

// Above this old×new product the exact LCS DP is too much work for a badge on the main path, so we
// fall back to the order-insensitive multiset estimate. 2M cells ≈ a 1400-line file against itself.
private const val LCS_CELL_BUDGET = 2_000_000L

/**
 * Added/removed line counts going from [old] to [new].
 *
 * Uses the length of the two files' longest common subsequence of lines: everything in `new` beyond
 * the common run is an addition, everything in `old` beyond it a removal. A null/blank [old] (the
 * origin version has no predecessor) makes every line an addition. Very large pairs fall back to a
 * multiset estimate so the computation stays cheap.
 */
fun lineDelta(old: String?, new: String): LineDelta {
    val oldLines = significantLines(old)
    val newLines = significantLines(new)
    if (oldLines.isEmpty()) return LineDelta(added = newLines.size, removed = 0)
    if (newLines.isEmpty()) return LineDelta(added = 0, removed = oldLines.size)

    if (oldLines.size.toLong() * newLines.size > LCS_CELL_BUDGET) {
        return multisetDelta(oldLines, newLines)
    }
    val common = lcsLength(oldLines, newLines)
    return LineDelta(added = newLines.size - common, removed = oldLines.size - common)
}

/** Length of the longest common subsequence of two line lists, in O(min(n,m)) rolling space. */
private fun lcsLength(a: List<String>, b: List<String>): Int {
    // Iterate over the longer list outside so the rolling row spans the shorter one.
    val (outer, inner) = if (a.size >= b.size) a to b else b to a
    val prev = IntArray(inner.size + 1)
    val curr = IntArray(inner.size + 1)
    for (i in outer.indices) {
        val outerLine = outer[i]
        for (j in inner.indices) {
            curr[j + 1] = if (outerLine == inner[j]) {
                prev[j] + 1
            } else {
                maxOf(prev[j + 1], curr[j])
            }
        }
        System.arraycopy(curr, 0, prev, 0, curr.size)
    }
    return prev[inner.size]
}

/**
 * Order-insensitive fallback: for each distinct line, surplus copies in `new` are additions and
 * surplus copies in `old` are removals. Cheaper than LCS and stable, at the cost of treating a
 * moved line as unchanged (which, for a badge, is the right instinct anyway).
 */
private fun multisetDelta(old: List<String>, new: List<String>): LineDelta {
    val oldCounts = old.groupingBy { it }.eachCount()
    val newCounts = new.groupingBy { it }.eachCount()
    var added = 0
    var removed = 0
    for (line in oldCounts.keys + newCounts.keys) {
        val delta = (newCounts[line] ?: 0) - (oldCounts[line] ?: 0)
        if (delta > 0) added += delta else removed += -delta
    }
    return LineDelta(added = added, removed = removed)
}

/**
 * Build the per-version deltas for a lineage's [versions] (any order), each measured against the
 * previous version by number. The result is sorted ascending; the first entry is the origin.
 */
fun versionDeltas(versions: List<ArtifactVersion>): List<VersionDelta> {
    val ordered = versions.sortedBy { it.versionNumber }
    var prior: String? = null
    return ordered.mapIndexed { index, version ->
        val delta = lineDelta(prior, version.content)
        prior = version.content
        VersionDelta(
            version = version.versionNumber,
            delta = delta,
            lineCount = significantLines(version.content).size,
            isFirst = index == 0,
        )
    }
}
