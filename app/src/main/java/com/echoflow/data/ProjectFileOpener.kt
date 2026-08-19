package com.echoflow.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a project file to the user's default external viewer — the "Open as PDF / Word / Excel …"
 * long-press action on the Files screen. The in-app path (Open as Markdown) is handled separately by
 * the document reader; this file only deals with leaving the app.
 *
 * Project files are copied into private storage under their doc id with no extension, which confuses
 * external viewers (they lean on the name/extension). So each open copies the one file into
 * `cache/shared_docs` under its real display name and shares *that* through [FileProvider], granting
 * the target app a one-shot read permission. Nothing in app storage is exposed directly.
 */
object ProjectFileOpener {

    /** How a project file presents to the user — drives the long-press action's label and icon. */
    enum class Kind { PDF, WORD, SPREADSHEET, SLIDES, IMAGE, OTHER }

    fun kindOf(document: ProjectDocument): Kind {
        val ext = document.name.substringAfterLast('.', "").lowercase()
        val mime = document.mimeType.lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> Kind.PDF
            ext in WORD_EXTS || "word" in mime || "opendocument.text" in mime -> Kind.WORD
            ext in SHEET_EXTS || "sheet" in mime || "excel" in mime || mime.endsWith("/csv") -> Kind.SPREADSHEET
            ext in SLIDE_EXTS || "presentation" in mime || "powerpoint" in mime -> Kind.SLIDES
            mime.startsWith("image/") -> Kind.IMAGE
            else -> Kind.OTHER
        }
    }

    /** The verb shown in the long-press menu, e.g. "Open as PDF". */
    fun openLabel(kind: Kind): String = when (kind) {
        Kind.PDF -> "Open as PDF"
        Kind.WORD -> "Open as Word"
        Kind.SPREADSHEET -> "Open as spreadsheet"
        Kind.SLIDES -> "Open as slides"
        Kind.IMAGE -> "Open image"
        Kind.OTHER -> "Open file"
    }

    /**
     * Launch the user's default viewer for [document]. Returns false when the file is gone or no
     * installed app can open the type, so the caller can surface an honest "nothing can open this".
     */
    fun openExternally(context: Context, document: ProjectDocument): Boolean {
        val source = File(document.filePath)
        if (!source.exists()) return false
        val mime = resolveMime(document)

        val shared = runCatching {
val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
// Clean up stale prior copies, but keep recent ones so external viewers don't lose their backing file.
dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24L * 60L * 60L * 1000L }?.forEach { it.delete() }
File(dir, safeFileName(document, mime)).also { source.copyTo(it, overwrite = true) }
        }.getOrNull() ?: return false

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
        }.getOrNull() ?: return false

val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, mime)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun resolveMime(document: ProjectDocument): String {
        val declared = document.mimeType
        if (declared.isNotBlank() && declared != "application/octet-stream" && '/' in declared) return declared
        val ext = document.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** The display name, stripped of any path parts, with an extension appended if it lacks one. */
    private fun safeFileName(document: ProjectDocument, mime: String): String {
        val raw = document.name.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { "document" }
        val ext = raw.substringAfterLast('.', "")
        if (ext.isNotBlank() && ext.length <= 5) return raw
        val guessed = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        return if (guessed != null) "$raw.$guessed" else raw
    }

    private const val SHARED_DIR = "shared_docs"
    private val WORD_EXTS = setOf("doc", "docx", "docm", "odt", "rtf")
    private val SHEET_EXTS = setOf("xls", "xlsx", "xlsm", "xlsb", "ods", "csv")
    private val SLIDE_EXTS = setOf("ppt", "pptx", "odp")
}
