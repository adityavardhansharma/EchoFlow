package com.echoflow.weave.core.eval

/** Deterministic randomness, 03 §10.10. All arithmetic is unsigned 64-bit wrapping. */
object WeaveRandom {
    private const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    fun seedBits(seed: WValue, f: Formatter? = null): Long = when (seed) {
        is WNum -> java.lang.Double.doubleToRawLongBits(seed.v)
        else -> fnv1a64((f ?: Formatter()).auto(seed).toByteArray(Charsets.UTF_8))
    }

    fun fnv1a64(bytes: ByteArray): Long {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (b in bytes) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        return h
    }

    fun splitmix64(z0: Long): Long {
        var z = z0 + GOLDEN
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    fun rand(seed: WValue, i: Long, f: Formatter? = null): Double =
        (splitmix64(seedBits(seed, f) + i * GOLDEN) ushr 11) * (1.0 / (1L shl 53))

    fun randInt(seed: WValue, i: Long, lo: Double, hi: Double, f: Formatter? = null): Double =
        lo + Math.floor(rand(seed, i, f) * (hi - lo + 1))
}
