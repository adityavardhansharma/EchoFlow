package com.echoflow.data.extract

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Test seam so [FileExtractor] unit tests never touch ML Kit. */
interface ImageTextRecognizer {
    suspend fun ocrImage(file: File): String?
    suspend fun ocrPdf(file: File, maxPages: Int = 30): String?
}

/**
 * Tier 2: on-device ML Kit OCR. Unbundled — the model downloads via Play Services on first
 * use. "Model not yet downloaded / unavailable" is a normal miss (null), never a crash.
 */
class OcrExtractor(@Suppress("unused") context: Context) : ImageTextRecognizer {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun ocrImage(file: File): String? = withContext(Dispatchers.IO) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        try {
            recognizeBlocking(InputImage.fromBitmap(bmp, 0)).takeIf { it.isNotBlank() }
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    override suspend fun ocrPdf(file: File, maxPages: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val sb = StringBuilder()
                    val pages = minOf(renderer.pageCount, maxPages)
                    for (i in 0 until pages) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(
                                (page.width * 2).coerceAtLeast(1),
                                (page.height * 2).coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            try {
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val text = recognizeBlocking(InputImage.fromBitmap(bmp, 0))
                                if (text.isNotBlank()) {
                                    if (sb.isNotEmpty()) sb.append("\n\n")
                                    sb.append(text)
                                }
                            } finally {
                                if (!bmp.isRecycled) bmp.recycle()
                            }
                        }
                    }
                    sb.toString().takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    private suspend fun recognizeBlocking(img: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(img)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text.orEmpty()) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }
}
