package com.echoflow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.edge.litertlm.Content
import java.io.ByteArrayOutputStream
import java.io.File

/** Android file, memory and image preparation kept outside inference session orchestration. */
internal class LocalLlmPlatformSupport(private val context: Context) {
    fun modelFile(model: LocalModel): File =
        File(File(context.filesDir, "models"), model.fileName)

    fun checkRamBudget(file: File) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val estimatedPeakBytes = (file.length() * 1.8).toLong()
        if (memory.totalMem in 1 until estimatedPeakBytes) {
            val needGb = estimatedPeakBytes / (1024.0 * 1024 * 1024)
            throw Exception(
                "This model needs roughly %.1f GB of RAM, more than this device has. Try a smaller model.".format(needGb),
            )
        }
    }

    fun friendlyLoadError(error: Throwable): String = when {
        error is OutOfMemoryError || error.message?.contains("memory", ignoreCase = true) == true ->
            "Not enough memory to load this model on this device. Try a smaller model."
        else ->
            "Could not load the model — the file may be corrupt or unsupported. " +
                "Re-download or re-import it. (${error.message?.take(120)})"
    }

    fun imageContentFromUri(uriString: String?): Content? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver
            val raw = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size().toLong() + count <= 20L * 1024 * 1024) { "Image exceeds 20 MB." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            val sample = imageSampleSize(bounds.outWidth, bounds.outHeight) ?: return null
            val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            try {
                val longest = maxOf(decoded.width, decoded.height)
                val bitmap = if (longest > MAX_IMAGE_EDGE) {
                    val scale = MAX_IMAGE_EDGE.toFloat() / longest
                    Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1), true)
                } else decoded
                try {
                    val output = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
                    Content.ImageBytes(output.toByteArray())
                } finally { if (bitmap !== decoded) bitmap.recycle() }
            } finally { decoded.recycle() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        internal fun imageSampleSize(width: Int, height: Int): Int? {
            if (width <= 0 || height <= 0) return null
            var sample = 1
            while (maxOf(width, height).toLong() / sample > MAX_IMAGE_EDGE * 2L) {
                if (sample >= (1 shl 20)) return null
                sample *= 2
            }
            return sample
        }
        const val MAX_IMAGE_EDGE = 768
        const val JPEG_QUALITY = 85
    }
}
