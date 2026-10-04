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

    /**
     * Turns a finished capture into the chat's copy and returns its URI, or null when nothing was
     * taken (cancelled, or a camera app that reports success but writes no bytes). The raw shot is
     * always deleted.
     */
    suspend fun finish(context: Context, raw: File, saved: Boolean): Uri? = withContext(Dispatchers.IO) {
        try {
            if (!saved || !raw.isFile || raw.length() == 0L) return@withContext null
            val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
            val out = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val bitmap = runCatching { decodeUpright(raw) }.getOrNull()
            if (bitmap != null) {
                val ok = out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bitmap.recycle()
                if (!ok) out.delete()
            }
            // Undecodable but sendable (an unusual format from some camera app): keep it as taken.
            if (!out.isFile && raw.length() <= CappedAttachmentBytes.MAX_BYTES) raw.copyTo(out, overwrite = true)
            if (out.isFile) uriFor(context, out) else null
        } finally {
            raw.delete()
        }
    }

    /** The stored chat photo [uri] points at, or null when it is not one (a gallery pick, a PDF, ...). */
    fun photoFileFor(context: Context, uri: String): File? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        if (parsed.authority != "${context.packageName}.fileprovider") return null
        val segments = parsed.pathSegments
        if (segments.size != 2 || segments[0] != PHOTO_DIR) return null
        val dir = File(context.filesDir, PHOTO_DIR).canonicalFile
        return File(dir, segments[1]).canonicalFile.takeIf { it.parentFile == dir }
    }

    /**
     * Deletes stored chat photos that no message points at any more. Checked: [candidates] (a deleted
     * chat's photos, at any age) plus every stored photo older than a day, which also catches shots
     * that were taken but never sent, or edited out of a turn. [keep] protects photos still staged in
     * the composer. [isReferenced] is asked by file name, after the deleted chat's rows are gone, so
     * a photo another chat still shows survives.
     */
    suspend fun deleteUnreferenced(
        context: Context,
        candidates: Collection<File>,
        keep: Set<File>,
        isReferenced: suspend (fileName: String) -> Boolean,
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, PHOTO_DIR)
        val cutoff = System.currentTimeMillis() - STALE_RAW_MS
        val stale = dir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }.orEmpty()
        val keepCanonical = keep.map { it.canonicalFile }.toSet()
        (candidates + stale)
            .map { it.canonicalFile }
            .distinct()
            .filter { it.isFile && it !in keepCanonical && !isReferenced(it.name) }
            .forEach { it.delete() }
    }

    /** Decodes at no more than [MAX_EDGE] on the long edge, with EXIF orientation applied. */
    private fun decodeUpright(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder samples while decoding and applies EXIF orientation itself.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = MAX_EDGE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
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
}
