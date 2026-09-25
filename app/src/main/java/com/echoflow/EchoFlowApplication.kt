package com.echoflow

import android.app.Application
import com.echoflow.data.usage.UsageLedger

/** Process start-up. Installed before any Activity, worker or service so every request is itemised. */
class EchoFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UsageLedger.install(this)
    }
}
