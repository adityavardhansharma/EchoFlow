package com.echoflow.data.extract

import android.util.Log
import dev.firecrawl.anydoc.AnyDoc
import dev.firecrawl.anydoc.ConvertException

/**
 * Tier 1: the bundled JNI anydoc library (`libanydoc.so`), same wrapper as the
 * trialanydoc app. Never throws — every failure is a [Result] so the caller can
 * fall through to OCR / provider. Callers must be on Dispatchers.IO.
 */
object Anydoc : DocumentParser {
    private const val TAG = "Anydoc"

    /** true when [libanydoc.so] loaded for this device's ABI. */
    override val available: Boolean by lazy {
        runCatching { AnyDoc.version(); true }.getOrDefault(false)
    }

    sealed interface Result {
        data class Text(val markdown: String) : Result
        /** anydoc declined — the file may still be OCR-able. */
        data object TryOcr : Result
        data class Failed(val reason: String) : Result
    }

    override fun convert(bytes: ByteArray, filename: String): Result = try {
        val md = AnyDoc.toMarkdownBytes(bytes, fileName = filename)
        if (md.isNotBlank()) Result.Text(md) else Result.TryOcr
    } catch (e: ConvertException) {
        when (e.code) {
            "encrypted" -> Result.Failed("encrypted")
            "resourceLimit" -> Result.Failed("too large")
            "unsupported", "malformed", "missingPart" -> {
                Log.d(TAG, "convert declined for $filename: ${e.code}")
                Result.TryOcr
            }
            else -> Result.Failed(e.code)
        }
    } catch (_: UnsatisfiedLinkError) {
        Result.TryOcr
    } catch (t: Throwable) {
        Log.w(TAG, "convert failed for $filename", t)
        Result.Failed(t.javaClass.simpleName)
    }
}

/** Test seam for [FileExtractor] so unit tests never load the native library. */
interface DocumentParser {
    val available: Boolean
    fun convert(bytes: ByteArray, filename: String): Anydoc.Result
}
