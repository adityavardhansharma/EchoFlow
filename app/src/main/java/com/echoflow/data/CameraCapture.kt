package com.echoflow.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Chat photos taken with the device's own camera app (Samsung Camera, Pixel Camera, ...) through
 * `ACTION_IMAGE_CAPTURE`, so every phone gets its native capture pipeline with no CAMERA permission.
 *
 * The camera app writes a full-resolution shot into [RAW_DIR] (cache). [finish] then turns it into
 * what the chat keeps: upright (EXIF orientation baked in), stripped of metadata (no GPS location
 * leaves the device), and capped at [MAX_EDGE] px on the long edge — every vision API downsizes
 * past that anyway, and a raw 50–200 MP frame would otherwise blow the per-image limits. The result
 * lives in [PHOTO_DIR] under files/, because the message row keeps its URI and cache can be wiped.
 */
internal object CameraCapture {
    private const val RAW_DIR = "camera"
    private const val PHOTO_DIR = "camera_photos"
    private const val MAX_EDGE = 2560
    private const val JPEG_QUALITY = 92
    private const val STALE_RAW_MS = 24L * 60 * 60 * 1000

    /** True when the device has a camera *and* a camera app that answers `ACTION_IMAGE_CAPTURE`. */
    fun isAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(pm) != null
    }

    /** A fresh file for the camera app to write into. Clears raw shots abandoned by earlier runs. */
    fun newRawFile(context: Context): File {
        val dir = File(context.cacheDir, RAW_DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - STALE_RAW_MS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        return File(dir, "raw_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** What came of one trip to the camera app. */
    sealed interface Outcome {
        /** The chat's copy; share it with [uriFor]. */
        data class Taken(val file: File) : Outcome
        /** Cancelled, or a camera app that reports success but writes no bytes. Nothing to say. */
        data object NoShot : Outcome
        /** A shot was taken but could not be made safe to send (undecodable, or storage full). */
        data object Failed : Outcome
    }

    /**
     * Turns a finished capture into the chat's copy. Only the re-encoded copy is ever attached:
     * re-encoding is what drops the metadata (GPS included), so a shot that can't be decoded or
     * written is [Outcome.Failed], never sent as taken. The raw shot is always deleted.
     */
    suspend fun finish(context: Context, raw: File, saved: Boolean): Outcome = withContext(Dispatchers.IO) {
        try {
            if (!saved || !raw.isFile || raw.length() == 0L) return@withContext Outcome.NoShot
            val bitmap = runCatching { decodeUpright(raw) }.getOrNull() ?: return@withContext Outcome.Failed
            val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
            val out = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val ok = try {
                runCatching {
                    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                }.getOrDefault(false)
            } finally {
                bitmap.recycle()
            }
            if (ok && out.length() > 0L) Outcome.Taken(out)
            else Outcome.Failed.also { out.delete() }
        } finally {
            raw.delete()
        }
    }

    /** Decodes at no more than [MAX_EDGE] on the long edge, with EXIF orientation applied. */
    private fun decodeUpright(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF orientation itself. It samples by a power of two rather than
            // to a target size, which holds whichever way the frame is turned; the exact cap is
            // applied after decoding.
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.setTargetSampleSize(sampleFor(max(info.size.width, info.size.height)))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
            if (scale >= 1f) return decoded
            return Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        val sample = sampleFor(longEdge)
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val matrix = Matrix()
        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    /** The largest power-of-two sample that keeps the long edge at or above [MAX_EDGE]. */
    private fun sampleFor(longEdge: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }
}
