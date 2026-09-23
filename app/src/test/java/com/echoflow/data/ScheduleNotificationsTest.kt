package com.echoflow.data

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ScheduleNotificationsTest {
    @Test fun resultSurvivesForegroundNotificationCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val foregroundId = 0x3400_0123
        val foreground = ScheduleNotifications.running(context, foregroundId, "Reminder")
        notifications.notify(foregroundId, foreground.notification)
        ScheduleNotifications.finished(context, foregroundId, "Reminder", "Your answer", "chat-id")

        // WorkManager removes its foreground ID after the worker returns.
        notifications.cancel(foregroundId)

        val remaining = notifications.activeNotifications.single()
        assertNotEquals(foregroundId, remaining.id)
        assertEquals("Your answer", remaining.notification.extras.getString("android.text"))
        assertNotNull(remaining.notification.contentIntent)
    }

    @Test fun failureSurvivesForegroundNotificationCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val id = 0x3400_0124
        notifications.notify(id, ScheduleNotifications.running(context, id, "Reminder").notification)
        ScheduleNotifications.finished(context, id, "Reminder", "Could not finish", null)
        notifications.cancel(id)
        assertEquals("Could not finish", notifications.activeNotifications.single().notification.extras.getString("android.text"))
    }
}
