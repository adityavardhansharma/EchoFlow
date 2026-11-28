package com.echoflow.weave.compose.chart

/**
 * Largest-Triangle-Three-Buckets downsampling (04 §12.5): a series with more points than
 * `2 × plot width in px` is reduced before drawing. It works on already-projected coordinates,
 * never on `WValue`s or bindings, so it can't reach the evaluator (05 §5, T-465). Tooltips keep
 * the full data.
 */
object Lttb {

    /**
     * Indices of the points to keep, in order, for points sorted by x. Keeps everything when
     * there are at most [threshold] points or [threshold] < 3; otherwise keeps the first, the last
     * and one point per bucket in between.
     */
    fun indices(x: FloatArray, y: FloatArray, threshold: Int): IntArray {
        val n = minOf(x.size, y.size)
        if (threshold >= n || threshold < 3) return IntArray(n) { it }
        val out = IntArray(threshold)
        out[0] = 0
        val every = (n - 2).toDouble() / (threshold - 2)
        var a = 0
        for (i in 0 until threshold - 2) {
            // average of the next bucket
            val nextStart = ((i + 1) * every).toInt() + 1
            val nextEnd = minOf(((i + 2) * every).toInt() + 1, n)
            var avgX = 0.0; var avgY = 0.0
            val count = (nextEnd - nextStart).coerceAtLeast(1)
            for (j in nextStart until nextEnd) { avgX += x[j]; avgY += y[j] }
            if (nextEnd > nextStart) { avgX /= count; avgY /= count } else { avgX = x[n - 1].toDouble(); avgY = y[n - 1].toDouble() }
            // the point in this bucket with the largest triangle against a and the average
            val start = (i * every).toInt() + 1
            val end = minOf(((i + 1) * every).toInt() + 1, n - 1)
            var best = start
            var bestArea = -1.0
            for (j in start until end) {
                val area = kotlin.math.abs((x[a] - avgX) * (y[j] - y[a]) - (x[a] - x[j]) * (avgY - y[a]))
                if (area > bestArea) { bestArea = area; best = j }
            }
            out[i + 1] = best
            a = best
        }
        out[threshold - 1] = n - 1
        return out
    }

    /** [points] reduced to at most [threshold], when it has more. */
    fun <T> downsample(points: List<T>, threshold: Int, x: (T) -> Float, y: (T) -> Float): List<T> {
        if (points.size <= threshold || threshold < 3) return points
        val keep = indices(FloatArray(points.size) { x(points[it]) }, FloatArray(points.size) { y(points[it]) }, threshold)
        return keep.map { points[it] }
    }
}
