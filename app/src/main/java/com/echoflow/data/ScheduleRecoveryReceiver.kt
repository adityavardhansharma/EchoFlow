package com.echoflow.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** WorkManager restores its own jobs; this also repairs Room-to-work handoff gaps. */
class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ScheduleManager(context.applicationContext).reconcile(replaceQueued = true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("ScheduleRecovery", "Could not repair scheduled work", e) }
            finally { pending.finish() }
        }
    }
}
