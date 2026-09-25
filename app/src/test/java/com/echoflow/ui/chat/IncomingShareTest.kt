package com.echoflow.ui.chat

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncomingShareTest {
    @Test fun `shared text becomes the draft`() {
        val share = IncomingShare.from(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "  hello  "),
        )
        assertEquals(IncomingShare("hello", emptyList()), share)
    }

    @Test fun `a shared link keeps its page title`() {
        val share = IncomingShare.from(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "A page")
                .putExtra(Intent.EXTRA_TEXT, "https://example.com"),
        )
        assertEquals("A page\nhttps://example.com", share?.text)
    }

    @Test fun `selected text arrives through process text`() {
        val share = IncomingShare.from(
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "selected words"),
        )
        assertEquals("selected words", share?.text)
    }

    @Test fun `shared files are collected from single and multiple sends`() {
        val one = Uri.parse("content://files/one.pdf")
        val two = Uri.parse("content://files/two.png")
        assertEquals(
            listOf(one),
            IncomingShare.from(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, one))?.files,
        )
        assertEquals(
            listOf(one, two),
            IncomingShare.from(
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(one, two)),
            )?.files,
        )
    }

    @Test fun `other intents and empty shares are ignored`() {
        assertNull(IncomingShare.from(Intent(Intent.ACTION_MAIN)))
        assertNull(IncomingShare.from(Intent(Intent.ACTION_SEND).setType("text/plain")))
        assertNull(IncomingShare.from(null))
    }
}
