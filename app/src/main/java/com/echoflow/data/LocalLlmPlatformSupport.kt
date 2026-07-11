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
            val raw = context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
                ?: return null
            var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > MAX_IMAGE_EDGE) {
                val scale = MAX_IMAGE_EDGE.toFloat() / longest
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true,
                )
            }
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            Content.ImageBytes(output.toByteArray())
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_IMAGE_EDGE = 768
        const val JPEG_QUALITY = 85
    }
}
