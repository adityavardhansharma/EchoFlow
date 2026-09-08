package com.echoflow

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.DeepResearchForegroundService
import com.echoflow.data.KeepAliveService
import com.echoflow.data.ReplyNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class UpdateCompatibilityContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `manifest keeps update-sensitive component identities`() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES,
        )
        val activities = info.activities.orEmpty().associateBy { it.name }
        val services = info.services.orEmpty().associateBy { it.name }
        assertTrue(activities.containsKey(MainActivity::class.java.name))
        assertTrue(activities.getValue(MainActivity::class.java.name).exported)
        assertFalse(services.getValue(KeepAliveService::class.java.name).exported)
        assertFalse(services.getValue(DeepResearchForegroundService::class.java.name).exported)
    }

    @Test fun `notification deep-link extra remains stable`() {
        assertEquals("open_chat_id", ReplyNotifications.EXTRA_OPEN_CHAT)
        assertEquals("com.echoflow.research.CANCEL", DeepResearchForegroundService.ACTION_CANCEL)
        assertEquals("com.echoflow.research.RESUME", DeepResearchForegroundService.ACTION_RESUME)
        assertEquals("run_id", DeepResearchForegroundService.EXTRA_RUN_ID)
        assertEquals("com.echoflow.keepalive.STOP", KeepAliveService.ACTION_STOP)
        assertEquals("text", KeepAliveService.EXTRA_TEXT)
    }

    @Test fun `backup rules retain database while excluding device-bound secrets and models`() {
        val excludes = excludesFrom(R.xml.backup_rules)
        assertTrue("sharedpref" to "secure_settings_prefs.xml" in excludes)
        assertTrue("sharedpref" to "settings_prefs.xml" in excludes)
        assertTrue("file" to "models/" in excludes)
        assertFalse(excludes.any { it.first == "database" })
    }

    @Test fun `data extraction rules retain database for cloud and device transfer`() {
        val excludes = excludesFrom(R.xml.data_extraction_rules)
        assertFalse(excludes.any { it.first == "database" })
        assertTrue(excludes.count { it == "sharedpref" to "secure_settings_prefs.xml" } >= 2)
        assertTrue(excludes.count { it == "file" to "models/" } >= 2)
    }

    @Test fun `installed builds always have a positive update version`() {
        assertTrue(BuildConfig.VERSION_CODE > 0)
    }

    private fun excludesFrom(resource: Int): List<Pair<String, String>> {
        val parser = context.resources.getXml(resource)
        return buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                    add(parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path"))
                }
                parser.next()
            }
        }
    }
}
