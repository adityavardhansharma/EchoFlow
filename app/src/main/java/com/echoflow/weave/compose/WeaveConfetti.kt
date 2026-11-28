package com.echoflow.weave.compose

import com.echoflow.weave.runtime.WeaveBlockController

/** `!confetti`: the block surface draws [com.echoflow.weave.compose.motion.ConfettiOverlay]. */
object WeaveConfetti {
    fun burst(block: WeaveBlockController) { block.confetti++ }
}
