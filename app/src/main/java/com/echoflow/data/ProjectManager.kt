package com.echoflow.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.echoflow.data.extract.FileExtractor
import com.echoflow.data.extract.OcrExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns the Projects store. Mirrors how [ArtifactManager] fronts the artifact tables: the UI observes
 * the rows this writes. A project bundles a custom instruction and a set of reference documents; the
 * documents' extracted text and the instruction are assembled by [buildSystemContext] into the
 * addendum a project's chats prepend to their system prompt.
 *
 * Imported files are copied into app storage so they outlive the source Uri; on removal (and on
 * project delete) those files are swept here, since Room can't touch the filesystem.
 */
class ProjectManager(
    private val context: Context,
    private val projectDao: ProjectDao,
    private val projectDocumentDao: ProjectDocumentDao,
    private val chatDao: ChatDao,
    private val extractor: FileExtractor = FileExtractor(ocr = OcrExtractor(context)),
) {
    /**
     * Copy and parse at most [EXTRACT_BATCH_SIZE] files at once. anydoc loads the whole
     * file (up to 25 MB) into a ByteArray, so four in flight is ~100 MB peak — enough to
     * ingest a handful of large docs quickly, not enough to OOM a mid-range phone if the
     * user dumps a folder of twenty.
     */
    private val copySlots = Semaphore(EXTRACT_BATCH_SIZE)
    private val extractSlots = Semaphore(EXTRACT_BATCH_SIZE)
    private val rowLocks = mutableMapOf<String, Mutex>()
    private fun rowLock(documentId: String): Mutex = synchronized(rowLocks) {
        rowLocks.getOrPut(documentId) { Mutex() }
    }
    fun observeProjects(): Flow<List<Project>> = projectDao.observeAll()
    fun observeProject(id: String): Flow<Project?> = projectDao.observeById(id)
    fun observeDocuments(projectId: String): Flow<List<ProjectDocument>> =
        projectDocumentDao.observeForProject(projectId)
    fun observeDocumentCount(projectId: String): Flow<Int> =
        projectDocumentDao.countForProject(projectId)
    fun observeChats(projectId: String): Flow<List<ChatThread>> =
        chatDao.getThreadsByProject(projectId)

    suspend fun getProject(id: String): Project? = projectDao.getById(id)

    suspend fun createProject(name: String, colorIndex: Int = 0): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        projectDao.upsert(
            Project(
                id = id,
                name = name.trim().ifBlank { UNTITLED_PROJECT },
                instructions = "",
                colorIndex = colorIndex,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    suspend fun rename(id: String, name: String) =
        projectDao.rename(id, name.trim().ifBlank { UNTITLED_PROJECT }, System.currentTimeMillis())

    suspend fun setInstructions(id: String, instructions: String) =
        projectDao.setInstructions(id, instructions, System.currentTimeMillis())

    suspend fun setColor(id: String, colorIndex: Int) =
        projectDao.setColor(id, colorIndex, System.currentTimeMillis())

    suspend fun touch(id: String) = projectDao.touch(id, System.currentTimeMillis())

    /**
     * Delete a project. Its chats are returned to the drawer (projectId set null) rather than
     * deleted, its documents' files are swept, and the row delete cascades away the document rows.
     *
     * The Room parent is removed *before* the folder: a concurrent [addDocument] that recreates
     * the directory then cannot insert (FK) and falls into its dest-delete path. Sweeping first
     * let an import mkdir, register a row, then lose that row to this cascade with no exception
     * and a file the files UI cannot see.
     */
    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        chatDao.clearProjectAssignments(id)
        projectDao.delete(id)
        runCatching { projectDir(id).deleteRecursively() }
    }

    suspend fun assignChat(chatId: String, projectId: String?) {
        chatDao.setProjectId(chatId, projectId)
        if (projectId != null) touch(projectId)
    }

    /**
     * Import [uri] as a reference document: copy it into the project's folder, insert a
     * PENDING row immediately, then extract on this IO dispatcher. The Files UI observes the
     * Room flow so the row settles in place. Returns the stored row, or null if the file
     * couldn't be copied at all.
     *
     * [onAdmitted] fires once the row is in Room, *before* extraction waits on a batch
     * slot, so the Files header can drop this pick from the "queued" count without
     * waiting for anydoc.
     */
    suspend fun addDocument(
        projectId: String,
        uri: Uri,
        onAdmitted: (() -> Unit)? = null,
    ): ProjectDocument? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = "Document"
        var size = 0L
        runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) cursor.getString(nameIndex)?.let { displayName = it }
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }

        // Reject oversized selections before we open a stream or allocate dest. SAF reports
        // SIZE when it can, and that check is free. The stream-time cap below still applies
        // when SIZE is missing or understated.
        if (size > MAX_IMPORT_BYTES) return@withContext null
        if (projectDao.getById(projectId) == null) return@withContext null

        // Keep the picker grant alive if the copy waits on a batch slot — OpenMultipleDocuments
        // URIs otherwise die with the activity result.
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val docId = UUID.randomUUID().toString()
        val dir = projectDir(projectId).apply { mkdirs() }
        val dest = File(dir, docId)
        // Bounded copy: stop (and delete) once the source exceeds the import cap, so a huge or
        // hostile file can't fill app storage. Any failure mid-write also sweeps the partial file
        // — otherwise it would linger unreferenced until the whole project is deleted.
        // Copy shares the same batch size as extract so twenty 25 MB picks don't all stream at once.
        val copied = copySlots.withPermit {
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMPORT_BYTES) throw java.io.IOException("File exceeds import cap")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        }
        if (!copied) {
            runCatching { dest.delete() }
            return@withContext null
        }
        if (size <= 0L) size = dest.length()

        val pending = ProjectDocument(
            id = docId,
            projectId = projectId,
            name = displayName,
            mimeType = mimeType,
            sizeBytes = size,
            filePath = dest.absolutePath,
            extractedText = null,
            addedAt = System.currentTimeMillis(),
            extractionStatus = ExtractionStatus.PENDING.name,
            extractionTier = null,
        )
        // Copy succeeded; the file is only reachable through a project_documents row. If
        // insert or touch throws, or the parent was deleted between copy and now (cascade
        // removes the row without throwing), dest must go with it — otherwise it sits
        // unreferenced and the files UI cannot remove it.
        try {
            projectDocumentDao.insert(pending)
            if (projectDao.getById(projectId) == null ||
                projectDocumentDao.getById(pending.id) == null
            ) {
                runCatching { projectDocumentDao.delete(pending.id) }
                runCatching { dest.delete() }
                return@withContext null
            }
            touch(projectId)
        } catch (t: Throwable) {
            runCatching { projectDocumentDao.delete(pending.id) }
            runCatching { dest.delete() }
            if (t is CancellationException) throw t
            return@withContext null
        }
        onAdmitted?.invoke()
        claimAndExtract(pending)
    }

    /**
     * Re-run extraction for legacy (`UNKNOWN`) rows and for imports interrupted mid-extract
     * (`PENDING` / `EXTRACTING`). Existing text files are stamped EXTRACTED / LEGACY_TEXT.
     * Shares [rowLock] with [addDocument] so the two cannot extract the same row, and
     * [extractSlots] so a backfill of many files still batches through anydoc.
     */
    suspend fun backfillProject(projectId: String) = withContext(Dispatchers.IO) {
        val docs = projectDocumentDao.getForProjectSync(projectId)
        coroutineScope {
            for (doc in docs) {
                launch {
                    val current = projectDocumentDao.getById(doc.id) ?: return@launch
                    when (current.status) {
                        ExtractionStatus.UNKNOWN -> {
                            if (current.hasText) {
                                projectDocumentDao.update(
                                    current.copy(
                                        extractionStatus = ExtractionStatus.EXTRACTED.name,
                                        extractionTier = ExtractionTier.LEGACY_TEXT.name,
                                    )
                                )
                            } else {
                                claimAndExtract(current)
                            }
                        }
                        ExtractionStatus.PENDING, ExtractionStatus.EXTRACTING -> claimAndExtract(current)
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * Take a parse slot (at most [EXTRACT_BATCH_SIZE] at once) and run [extractAndStore].
     * The per-row mutex is the claim: add and backfill both go through here, so a
     * PENDING row cannot be parsed twice.
     */
    private suspend fun claimAndExtract(document: ProjectDocument): ProjectDocument? {
        return rowLock(document.id).withLock {
            val current = projectDocumentDao.getById(document.id) ?: return@withLock null
            if (current.status.isBusy || current.status == ExtractionStatus.UNKNOWN) {
                extractSlots.withPermit { extractAndStore(current) }
            } else {
                current
            }
        }
    }

    private suspend fun extractAndStore(document: ProjectDocument): ProjectDocument {
        projectDocumentDao.update(document.copy(extractionStatus = ExtractionStatus.EXTRACTING.name))
        val result = runCatching {
            extractor.extract(File(document.filePath), document.mimeType, document.name)
        }.getOrElse {
            if (it is CancellationException) throw it
            FileExtractor.Result(null, ExtractionStatus.FAILED, null)
        }
        val updated = document.copy(
            extractedText = result.text,
            extractionStatus = result.status.name,
            extractionTier = result.tier?.name,
        )
        projectDocumentDao.update(updated)
        return updated
    }

    suspend fun documentsNeedingProvider(projectId: String): List<ProjectDocument> =
        projectDocumentDao.getForProjectSync(projectId)
            .filter { it.status == ExtractionStatus.NEEDS_PROVIDER }

    suspend fun removeDocument(document: ProjectDocument) = withContext(Dispatchers.IO) {
        runCatching { File(document.filePath).delete() }
        projectDocumentDao.delete(document.id)
        touch(document.projectId)
    }

    /**
     * The project's standing context, appended to the system prompt of every chat in it. Empty
     * when the project has neither an instruction nor any readable document, so a bare "folder"
     * project adds nothing to the prompt. Document text is budget-capped so a large corpus can't
     * blow the context window — the model is told when a document was truncated.
     */
    suspend fun buildSystemContext(projectId: String): String {
        val project = projectDao.getById(projectId) ?: return ""
        val docs = projectDocumentDao.getForProjectSync(projectId).filter { doc ->
            doc.hasText && (doc.status == ExtractionStatus.EXTRACTED || doc.status == ExtractionStatus.UNKNOWN)
        }
        val instructions = project.instructions.trim()
        if (instructions.isBlank() && docs.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n\n---\n")
        sb.append("The user is working inside a project called \"").append(project.name).append("\".")
        if (instructions.isNotBlank()) {
            sb.append("\n\nProject instructions (follow these for every message in this project):\n")
            sb.append(instructions)
        }
        if (docs.isNotEmpty()) {
            sb.append("\n\nReference documents attached to this project. Use them as background ")
            sb.append("knowledge when relevant, and cite them by name:\n")
            var budget = MAX_DOC_CONTEXT_CHARS
            for (doc in docs) {
                if (budget <= 0) {
                    sb.append("\n[Some documents were omitted to fit the context window.]")
                    break
                }
                val body = doc.extractedText.orEmpty()
                val slice = if (body.length > budget) body.take(budget) else body
                budget -= slice.length
                sb.append("\n### ").append(doc.name).append('\n').append(slice)
                if (slice.length < body.length) sb.append("\n[…document truncated…]")
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun projectDir(projectId: String): File =
        File(File(context.filesDir, "project_documents"), projectId)

    companion object {
        /** Fallback when the user supplies a blank project name. */
        const val UNTITLED_PROJECT = "Untitled project"

        /** Hard cap on an imported file's size (bytes) — larger selections are rejected. */
        private const val MAX_IMPORT_BYTES = 25L * 1024 * 1024

        /**
         * How many files copy or parse at once. Four is the memory bound: each anydoc
         * conversion holds the whole file (≤ 25 MB) as bytes. Picks above this wait in
         * PENDING until a slot frees.
         */
        const val EXTRACT_BATCH_SIZE = 4

        /** Combined document budget injected into any single system prompt. */
        private const val MAX_DOC_CONTEXT_CHARS = 24_000
    }
}
