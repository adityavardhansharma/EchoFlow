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
        val bmp = decodeBounded(file) ?: return@withContext null
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
                            val size = OcrBitmapBudget.pdfRenderSize(page.width, page.height)
                                ?: return@use
                            val bmp = runCatching {
                                Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
                            }.getOrNull() ?: return@use
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

    private fun decodeBounded(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = OcrBitmapBudget.sampleSize(bounds.outWidth, bounds.outHeight) ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        if (!OcrBitmapBudget.fits(bmp.width, bmp.height)) {
            if (!bmp.isRecycled) bmp.recycle()
            return null
        }
        return bmp
    }

    private suspend fun recognizeBlocking(img: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(img)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text.orEmpty()) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }
}

/**
 * Caps OCR bitmaps so a 25 MB JPEG or a huge PDF page box cannot allocate
 * width×height×4 bytes and OOM the process. Pure math so unit tests do not
 * need ML Kit or a renderer.
 */
internal object OcrBitmapBudget {
    const val MAX_PIXELS = 4_000_000L

    fun fits(width: Int, height: Int, maxPixels: Long = MAX_PIXELS): Boolean {
        if (width <= 0 || height <= 0) return false
        return width.toLong() * height.toLong() <= maxPixels
    }

    /**
     * Power-of-two [BitmapFactory.Options.inSampleSize], or null when even the
     * 1024× floor still exceeds [maxPixels]. Callers must not decode on null.
     */
    fun sampleSize(width: Int, height: Int, maxPixels: Long = MAX_PIXELS): Int? {
        if (width <= 0 || height <= 0) return null
        var sample = 1
        var w = width.toLong()
        var h = height.toLong()
        while (w * h > maxPixels) {
            if (sample >= 1024) return null
            sample *= 2
            w = width.toLong() / sample
            h = height.toLong() / sample
            if (w < 1L || h < 1L) return null
        }
        return sample
    }

    fun pdfRenderSize(pageWidth: Int, pageHeight: Int, maxPixels: Long = MAX_PIXELS): Pair<Int, Int>? {
        val rawW = pageWidth.toLong() * 2
        val rawH = pageHeight.toLong() * 2
        if (rawW <= 0L || rawH <= 0L) return null
        if (rawW * rawH <= maxPixels) {
            return rawW.toInt() to rawH.toInt()
        }
        val scale = kotlin.math.sqrt(maxPixels.toDouble() / (rawW * rawH).toDouble())
        val w = (rawW * scale).toInt().coerceAtLeast(1)
        val h = (rawH * scale).toInt().coerceAtLeast(1)
        if (w.toLong() * h.toLong() > maxPixels) return null
        return w to h
    }
}
