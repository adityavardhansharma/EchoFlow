package com.echoflow

import android.app.Application
import com.echoflow.data.AppVisibility
import com.echoflow.data.usage.UsageLedger

/** Process start-up. Installed before any Activity, worker or service so every request is itemised. */
class EchoFlowApplication : Application() {
    /**
     * The app's one composition root. Built on first use rather than in [onCreate], so a worker or
     * service that wakes the process doesn't pay for the UI graph (and its model seed) it never uses.
     * One per process: a recreated Activity reuses it instead of building a second
     * [com.echoflow.data.SettingsRepository] beside the one its surviving ViewModels hold.
     */
    val graph: EchoFlowAppGraph by lazy { EchoFlowAppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        UsageLedger.install(this)
        AppVisibility.install(this)
    }
}
