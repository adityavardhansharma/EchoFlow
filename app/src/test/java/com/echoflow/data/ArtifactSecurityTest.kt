package com.echoflow.data

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtifactSecurityTest {
    @Test fun `offline rendering blocks network and local file access`() {
        val view = WebView(ApplicationProvider.getApplicationContext<Context>())
        try {
            ArtifactWebSecurity.configure(view, true)
            assertTrue(view.settings.blockNetworkLoads)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            ArtifactWebSecurity.configure(view, false)
            assertFalse(view.settings.blockNetworkLoads)
            assertFalse(view.settings.allowFileAccess)
        } finally { view.destroy() }
    }

    @Test fun `report embeds renderer and encodes script closing text`() {
        val payload = "</ScRiPt><script>window.untrusted = true</script>"
        val html = ArtifactExport.reportHtml(ApplicationProvider.getApplicationContext(), "Report", payload)
        assertFalse(html.contains(payload))
        assertFalse(Regex("<script[^>]+src=", RegexOption.IGNORE_CASE).containsMatchIn(html))
        assertFalse(Regex("<link[^>]+href=", RegexOption.IGNORE_CASE).containsMatchIn(html))
        assertTrue(html.contains("data:font/woff2;base64,"))
    }

    @Test fun `offline policy precedes generated active content and survives export`() {
        val html = ArtifactWebSecurity.offlineHtml("<html><head><script src='https://example.com/x.js'></script></head></html>")
        assertTrue(html.startsWith("<meta http-equiv=\"Content-Security-Policy\""))
        assertTrue(html.contains("connect-src 'none'"))
        assertTrue(html.contains("form-action 'none'"))
    }
}
