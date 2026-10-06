package com.echoflow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraCaptureTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun cancelledCapture_attachesNothing_andDeletesRawFile() = runBlocking {
    val raw = CameraCapture.newRawFile(context).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    assertEquals(CameraCapture.Outcome.NoShot, CameraCapture.finish(context, raw, saved = false))
    assertFalse(raw.exists())
  }

  @Test
  fun emptyCapture_attachesNothing_andDeletesRawFile() = runBlocking {
    // Some camera apps report success without writing a byte.
    val raw = CameraCapture.newRawFile(context).apply { writeBytes(ByteArray(0)) }

    assertEquals(CameraCapture.Outcome.NoShot, CameraCapture.finish(context, raw, saved = true))
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
      references = { listOf("content://x/camera_photos/photo_shared.jpg") },
    )

    assertFalse(deletedChats.exists())
    assertTrue(sharedWithOtherChat.exists())
    assertTrue(staged.exists())
    assertTrue(freshUnsent.exists())
    assertFalse(staleUnsent.exists())
  }

  @Test
  fun undecodableCapture_isNeverAttachedAsTaken() = runBlocking {
    // Re-encoding is what strips metadata, so bytes that can't be decoded must not go out raw.
    val raw = CameraCapture.newRawFile(context).apply { writeBytes(ByteArray(64) { 7 }) }

    assertEquals(CameraCapture.Outcome.Failed, CameraCapture.finish(context, raw, saved = true))
    assertFalse(raw.exists())
  }

  /** A landscape-pixel JPEG the way phones store a portrait shot: EXIF says turn it 90°. */
  private fun rawPortraitShot(width: Int, height: Int): File {
    val raw = CameraCapture.newRawFile(context)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
    raw.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    ExifInterface(raw.path).apply {
      setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
      setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,25/1,0/1")
      setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
      saveAttributes()
    }
    return raw
  }

  private fun storedFile(outcome: CameraCapture.Outcome): File = (outcome as CameraCapture.Outcome.Taken).file

  @Test
  fun rotatedShot_comesOutUpright_withoutLocation() = runBlocking {
    val stored = storedFile(CameraCapture.finish(context, rawPortraitShot(400, 200), saved = true))

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(stored.path, bounds)
    assertEquals(200, bounds.outWidth)
    assertEquals(400, bounds.outHeight)
    val exif = ExifInterface(stored.path)
    assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    // Already turned, so no viewer may turn it again.
    assertTrue(
      exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in
        setOf(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface.ORIENTATION_NORMAL),
    )
  }

  @Test
  fun largeRotatedShot_isCappedOnItsLongEdge_inPortrait() = runBlocking {
    val stored = storedFile(CameraCapture.finish(context, rawPortraitShot(2600, 1300), saved = true))

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(stored.path, bounds)
    assertEquals(2560, bounds.outHeight)
    assertEquals(1280, bounds.outWidth)
  }
}
