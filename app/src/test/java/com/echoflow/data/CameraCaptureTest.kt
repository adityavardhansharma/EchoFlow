package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
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

  private fun storedPhoto(name: String, ageMs: Long = 0): File =
    File(context.filesDir, "camera_photos").apply { mkdirs() }.let { dir ->
      File(dir, name).apply {
        writeBytes(byteArrayOf(1))
        setLastModified(System.currentTimeMillis() - ageMs)
      }
    }

  @Test
  fun photoFileFor_mapsOnlyOurStoredPhotos() {
    val photo = storedPhoto("photo_1.jpg")
    val ours = "content://${context.packageName}.fileprovider/camera_photos/photo_1.jpg"

    assertEquals(photo.canonicalFile, CameraCapture.photoFileFor(context, ours))
    assertNull(CameraCapture.photoFileFor(context, "content://media/external/images/media/12"))
    assertNull(CameraCapture.photoFileFor(context, "content://${context.packageName}.fileprovider/shared_docs/a.pdf"))
    assertNull(CameraCapture.photoFileFor(context, "content://${context.packageName}.fileprovider/camera_photos/..%2Fsecret"))
  }

  @Test
  fun deleteUnreferenced_removesOnlyPhotosNothingPointsAt() = runBlocking {
    val dayAndMore = 25L * 60 * 60 * 1000
    val deletedChats = storedPhoto("photo_deleted.jpg")
    val sharedWithOtherChat = storedPhoto("photo_shared.jpg")
    val staged = storedPhoto("photo_staged.jpg")
    val freshUnsent = storedPhoto("photo_fresh.jpg")
    val staleUnsent = storedPhoto("photo_stale.jpg", ageMs = dayAndMore)

    CameraCapture.deleteUnreferenced(
      context,
      candidates = listOf(deletedChats, sharedWithOtherChat, staged),
      keep = setOf(staged),
      isReferenced = { it == "photo_shared.jpg" },
    )

    assertFalse(deletedChats.exists())
    assertTrue(sharedWithOtherChat.exists())
    assertTrue(staged.exists())
    assertTrue(freshUnsent.exists())
    assertFalse(staleUnsent.exists())
  }
}
