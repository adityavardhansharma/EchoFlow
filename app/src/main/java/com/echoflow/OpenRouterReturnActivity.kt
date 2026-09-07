package com.echoflow

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Brings the existing settings task back without creating a second MainActivity/ViewModel.
 * Authorization is handled only by the loopback listener; no intent data is forwarded.
 */
class OpenRouterReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ))
        finish()
    }
}
