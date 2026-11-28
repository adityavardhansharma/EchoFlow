package com.echoflow.weave.core

import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.WeaveRandom
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-290: frozen outputs of the reference implementation for seeds 0, 1, "x" and i = 0…9.
 * Generated on first commit; any change to `rand` fails this test.
 */
class RandomVectorsTest {
    private fun row(seed: WValue) = (0L..9L).map { WeaveRandom.rand(seed, it, W.fmt()) }

    @Test fun `frozen vectors`() {
        for ((seed, expected) in VECTORS) {
            val got = row(seed)
            for (i in got.indices) assertEquals("seed $seed i $i", expected[i], got[i], 0.0)
        }
    }

    companion object {
        val VECTORS: List<Pair<WValue, List<Double>>> = listOf(
            WNum(0.0) to listOf(0.8833108082136426, 0.43152799704850997, 0.026433771592597743, 0.9708819781538285, 0.10634669156721244, 0.32732576421812576, 0.17386786595968284, 0.771546556331567, 0.24568894884013137, 0.9520306913678265),
            WNum(1.0) to listOf(0.5345422839126993, 0.6256540836442631, 0.9345736974186606, 0.7015395710482343, 0.8726213623514772, 0.6651709068640048, 0.7558294115891947, 0.7187411950815937, 0.9180562420287584, 0.9174468651564877),
            WStr("x") to listOf(0.20120828434354443, 0.8085267624840695, 0.6433236341750462, 0.828109401457242, 0.41893360268305424, 0.7938299715344695, 0.7284297679136427, 0.6617452415781362, 0.9711143296225863, 0.34853114943748764),
        )
    }
}
