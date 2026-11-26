package com.echoflow.weave.core

/**
 * Limits, 05 §13.3. 05 places `Limits.kt` in `weave-runtime`; the checker and evaluator in
 * `weave-core` need the same numbers, so they live here and the runtime re-uses this object.
 */
object Limits {
    const val SOURCE_BYTES = 48 * 1024
    const val STATEMENTS = 400
    const val NODES_PER_BLOCK = 600
    const val NODES_VISIBLE_TOTAL = 3_000
    const val DEPTH = 24
    const val EVAL_STEPS = 20_000
    const val RECURSION = 64
    const val STATE_BYTES = 256 * 1024
    const val EAGER_LIST_ITEMS = 30
    const val RANGE_ITEMS = 10_000
    const val TIMERS_PER_BLOCK = 4
    const val MIN_TIMER_MS = 250L
    const val IMAGES_PER_BLOCK = 24
    const val IMAGE_BYTES = 4 * 1024 * 1024
    const val QUERIES_PER_BLOCK = 4
    const val MIN_QUERY_INTERVAL_MS = 60_000L
    const val ASKS_PER_HOUR = 20
    const val EFFECT_CHAIN = 8
    const val HISTORY_ENTRIES = 50
    const val JS_TIME_MS = 50L
    const val JS_MEMORY_BYTES = 16 * 1024 * 1024
    const val FRAME_BUDGET_NANOS = 2_000_000L
}
