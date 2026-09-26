package com.echoflow.ui.chat

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.echoflow.data.MessageAttachment
import com.echoflow.data.extract.ChatAttachmentExtractor
import com.echoflow.ui.PendingAttachment
import com.echoflow.ui.PendingAttachmentPolicy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the composer draft's files and extraction jobs. The supplied scope owns its lifetime;
 * navigation and successful sends clear the draft through [clearPendingAttachment].
 * The ViewModel observes read-only state and forwards errors to the conversation surface.
 */
internal class ChatAttachmentController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val maxAttachments: Int,
    private val onError: (String) -> Unit,
    private val extract: suspend (Uri, String) -> ChatAttachmentExtractor.Result =
        ChatAttachmentExtractor(application.contentResolver)::extract,
) {
    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()
    private val extractionJobs = mutableMapOf<String, Job>()
    // Shared-in files not yet checked against the model; the next reconcile says if any were dropped.
    private val unreconciledShares = mutableSetOf<String>()

    /** Restore a prompt's attachments when editing, cancelling parses from the previous draft. */
    fun restore(attachments: List<MessageAttachment>, extractLocally: Boolean) {
        cancelExtractionJobs()
        _pendingAttachments.value = attachments.map { att ->
            PendingAttachment(
                uri = att.uri,
                mimeType = att.mimeType,
                name = att.name,
                kind = if (att.mimeType.startsWith("image/", ignoreCase = true)) PendingAttachment.Kind.Image
                else PendingAttachment.Kind.Doc,
                state = PendingAttachment.State.Ready,
                extractedText = att.extractedText,
            )
        }
        if (extractLocally) extractMissingDocs()
    }

    /**
     * Stage a single attachment (an image, or a cloud/custom PDF), replacing whatever was staged.
     * This is the unchanged single-file path — images and cloud files are [State.Ready] at once,
     * never parsed on-device. The local multi-doc path is [addPendingDocs].
     */
    fun setPendingAttachment(uri: Uri, fallbackMimeType: String? = null, overrideName: String? = null) {
        scope.launch {
            val resolver = application.contentResolver
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mimeType = resolver.getType(uri) ?: fallbackMimeType ?: "image/jpeg"

            // Get display name. An override wins outright: a file the app generated has a UUID
            // for a name, and showing that tells the user nothing about why it is attached.
            val isPdf = mimeType.equals("application/pdf", ignoreCase = true)
            var displayName = if (isPdf) "Attached PDF" else "Attached Image"
            runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
            cancelExtractionJobs()
            _pendingAttachments.value = listOf(
                PendingAttachment(
                    uri = uri.toString(),
                    mimeType = mimeType,
                    name = overrideName ?: displayName,
                    kind = if (mimeType.startsWith("image/", ignoreCase = true)) PendingAttachment.Kind.Image
                    else PendingAttachment.Kind.Doc,
                    state = PendingAttachment.State.Ready,
                )
            )
        }
    }

    /**
     * Stage one or more documents for the local-model path (up to [maxAttachments] total),
     * appending to whatever is already staged, and kick off on-device extraction for each. Each chip
     * stays [State.Extracting] until anydoc yields Markdown (→ [State.Ready]) or declines
     * (→ [State.Failed], retry/remove in the composer). Extra picks over the cap are dropped.
     */
    fun addPendingDocs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val resolver = application.contentResolver
            for (uri in uris) {
                if (_pendingAttachments.value.size >= maxAttachments) break
                if (_pendingAttachments.value.any { it.uri == uri.toString() }) continue
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
                runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
                val attachment = PendingAttachment(
                    uri = uri.toString(),
                    mimeType = mimeType,
                    name = displayName,
                    kind = PendingAttachment.Kind.Doc,
                    state = PendingAttachment.State.Extracting,
                )
                _pendingAttachments.value = _pendingAttachments.value + attachment
                startExtraction(attachment)
            }
        }
    }

    /** Runs (or re-runs) on-device extraction for one staged doc, updating its chip in place. */
    private fun startExtraction(attachment: PendingAttachment) {
        extractionJobs.remove(attachment.id)?.cancel()
        val job = scope.launch {
            val result = extract(Uri.parse(attachment.uri), attachment.name)
            updateAttachment(attachment.id) { current ->
                when (result) {
                    is com.echoflow.data.extract.ChatAttachmentExtractor.Result.Text ->
                        current.copy(state = PendingAttachment.State.Ready, extractedText = result.markdown)
                    is com.echoflow.data.extract.ChatAttachmentExtractor.Result.Failed ->
                        current.copy(state = PendingAttachment.State.Failed, extractedText = null)
                }
            }
        }
        extractionJobs[attachment.id] = job
    }

    /** Retry the on-device parse for a failed doc chip (the composer's retry arrow). */
    fun retryPendingAttachment(id: String) {
        val target = _pendingAttachments.value.firstOrNull { it.id == id } ?: return
        if (target.kind != PendingAttachment.Kind.Doc) return
        updateAttachment(id) { it.copy(state = PendingAttachment.State.Extracting, extractedText = null) }
        startExtraction(target)
    }

    /** Remove one staged attachment (the composer's ✕). */
    fun removePendingAttachment(id: String) {
        extractionJobs.remove(id)?.cancel()
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id }
    }

    /**
     * Drop files this model/mode cannot send, collapse to one file off the local path, and
     * start parsing any Ready doc that still has no Markdown (cloud PDF → local switch, or edit).
     */
    fun reconcilePendingAttachments(
        imageAllowed: Boolean,
        pdfAllowed: Boolean,
        localFilesAllowed: Boolean,
    ) {
        val current = _pendingAttachments.value
        if (current.isEmpty()) return
        val sharedIds = unreconciledShares.toSet().also { unreconciledShares.clear() }
        val next = PendingAttachmentPolicy.keep(
            current,
            imageAllowed = imageAllowed,
            pdfAllowed = pdfAllowed,
            localFilesAllowed = localFilesAllowed,
            cap = maxAttachments,
        )
        if (next.map { it.id } != current.map { it.id }) {
            val keepIds = next.map { it.id }.toSet()
            extractionJobs.keys.filter { it !in keepIds }.forEach { extractionJobs.remove(it)?.cancel() }
            _pendingAttachments.value = next
            val droppedShares = sharedIds.count { id -> next.none { it.id == id } }
            if (droppedShares > 0 && next.isEmpty()) {
                onError("This model can't read the shared file. Pick another model and share it again.")
            } else if (next.size < current.size && !localFilesAllowed && next.isNotEmpty()) {
                onError("Only one file can go with this model. Extra files were dropped.")
            }
        }
        if (localFilesAllowed) extractMissingDocs()
    }

    private fun extractMissingDocs() {
        for (att in _pendingAttachments.value) {
            if (!PendingAttachmentPolicy.needsExtraction(att)) continue
            updateAttachment(att.id) { it.copy(state = PendingAttachment.State.Extracting, extractedText = null) }
            val updated = _pendingAttachments.value.firstOrNull { it.id == att.id } ?: continue
            startExtraction(updated)
        }
    }

    private fun updateAttachment(id: String, transform: (PendingAttachment) -> PendingAttachment) {
        _pendingAttachments.value = _pendingAttachments.value.map { if (it.id == id) transform(it) else it }
    }

    private fun cancelExtractionJobs() {
        extractionJobs.values.forEach { it.cancel() }
        extractionJobs.clear()
    }

    fun setPendingPastedImage(uri: Uri, fallbackMimeType: String? = null) {
        scope.launch {
            val cached = copyPastedImageToCache(uri, fallbackMimeType)
            if (cached == null) {
                onError("Could not paste image.")
            } else {
                setPendingAttachment(cached.first, cached.second)
            }
        }
    }

    private suspend fun copyPastedImageToCache(uri: Uri, fallbackMimeType: String?): Pair<Uri, String>? =
        withContext(Dispatchers.IO) {
            val app = application
            val resolver = app.contentResolver
            val mimeType = resolver.getType(uri) ?: fallbackMimeType ?: "image/png"
            if (!mimeType.startsWith("image/", ignoreCase = true)) return@withContext null

            runCatching {
                val dir = File(app.cacheDir, "pasted_images").apply { mkdirs() }
                val file = File.createTempFile("pasted_image_", ".${imageExtensionFor(mimeType)}", dir)
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                Uri.fromFile(file) to mimeType
            }.getOrNull()
        }

    /**
     * Stage files another app shared with EchoFlow. The sender's read grant only lasts as long
     * as the share, so each file is copied into cache first. Everything lands [State.Ready];
     * the composer's reconcile then drops what the current model cannot take and starts the
     * on-device parse where the local path needs it.
     */
    fun addSharedFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            var unreadable = 0
            for (uri in uris) {
                if (_pendingAttachments.value.size >= maxAttachments) break
                val copied = copySharedFileToCache(uri)
                if (copied == null) {
                    unreadable++
                    continue
                }
                val attachment = PendingAttachment(
                    uri = copied.uri.toString(),
                    mimeType = copied.mimeType,
                    name = copied.name,
                    kind = if (copied.mimeType.startsWith("image/", ignoreCase = true)) PendingAttachment.Kind.Image
                    else PendingAttachment.Kind.Doc,
                    state = PendingAttachment.State.Ready,
                )
                unreconciledShares += attachment.id
                _pendingAttachments.value = _pendingAttachments.value + attachment
            }
            if (unreadable > 0) {
                onError(if (unreadable == 1) "Could not read the shared file." else "Could not read $unreadable shared files.")
            }
        }
    }

    private class SharedFile(val uri: Uri, val mimeType: String, val name: String)

    private suspend fun copySharedFileToCache(uri: Uri): SharedFile? =
        withContext(Dispatchers.IO) {
            val resolver = application.contentResolver
            runCatching {
                var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Shared file"
                runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                }
                // Some senders hand over a bare file:// URI, which has no type; go by extension.
                val mimeType = resolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"
                // Each share gets its own folder so the file keeps its real name without clashing.
                val dir = File(File(application.cacheDir, "shared_in"), java.util.UUID.randomUUID().toString())
                    .apply { mkdirs() }
                val safeName = displayName.replace(Regex("[/\\\\]"), "_").ifBlank { "Shared file" }
                val file = File(dir, safeName)
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                SharedFile(Uri.fromFile(file), mimeType, displayName)
            }.getOrNull()
        }

    private fun imageExtensionFor(mimeType: String): String =
        when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "img"
        }

    fun clearPendingAttachment() {
        cancelExtractionJobs()
        unreconciledShares.clear()
        _pendingAttachments.value = emptyList()
    }

}
