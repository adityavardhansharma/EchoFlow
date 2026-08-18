package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class CappedAttachmentBytesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `reads a file under the cap`() {
        val file = tmp.newFile("ok.bin").apply { writeBytes(ByteArray(64) { 7 }) }
        val bytes = CappedAttachmentBytes.read(file.absolutePath) { null }
        assertEquals(64, bytes?.size)
        assertTrue(bytes!!.all { it == 7.toByte() })
    }

    @Test fun `rejects a file over the cap`() {
        val file = tmp.newFile("big.bin").apply { writeBytes(ByteArray(128)) }
        val over = with(CappedAttachmentBytes) { file.inputStream().use { it.readCapped(64) } }
        assertNull(over)
    }

    @Test fun `stream that stays under the cap is returned`() {
        val data = ByteArray(100) { 1 }
        val got = with(CappedAttachmentBytes) { ByteArrayInputStream(data).readCapped(100) }
        assertEquals(100, got?.size)
    }
}
