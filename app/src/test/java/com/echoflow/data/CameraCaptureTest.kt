package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraCaptureTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun cancelledCapture_attachesNothing_andDeletesRawFile() = runBlocking {
    val raw = CameraCapture.newRawFile(context).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    assertNull(CameraCapture.finish(context, raw, saved = false))
    assertFalse(raw.exists())
  }

  @Test
  fun emptyCapture_attachesNothing_andDeletesRawFile() = runBlocking {
    // Some camera apps report success without writing a byte.
    val raw = CameraCapture.newRawFile(context).apply { writeBytes(ByteArray(0)) }

    assertNull(CameraCapture.finish(context, raw, saved = true))
    assertFalse(raw.exists())
  }
}
