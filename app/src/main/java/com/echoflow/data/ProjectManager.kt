package com.echoflow.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
) {
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
     */
    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        chatDao.clearProjectAssignments(id)
        runCatching { projectDir(id).deleteRecursively() }
        projectDao.delete(id)
    }

    suspend fun assignChat(chatId: String, projectId: String?) {
        chatDao.setProjectId(chatId, projectId)
        if (projectId != null) touch(projectId)
    }

    /**
     * Import [uri] as a reference document: copy it into the project's folder and, for text
     * formats, extract its text for context injection. Returns the stored row, or null if the
     * file couldn't be read at all.
     */
    suspend fun addDocument(projectId: String, uri: Uri): ProjectDocument? = withContext(Dispatchers.IO) {
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

        val docId = UUID.randomUUID().toString()
        val dir = projectDir(projectId).apply { mkdirs() }
        val dest = File(dir, docId)
        // Bounded copy: stop (and delete) once the source exceeds the import cap, so a huge or
        // hostile file can't fill app storage. Any failure mid-write also sweeps the partial file
        // — otherwise it would linger unreferenced until the whole project is deleted.
        val copied = runCatching {
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
        if (!copied) {
            runCatching { dest.delete() }
            return@withContext null
        }
        if (size <= 0L) size = dest.length()

        val extracted = extractText(dest, mimeType, displayName)
        val document = ProjectDocument(
            id = docId,
            projectId = projectId,
            name = displayName,
            mimeType = mimeType,
            sizeBytes = size,
            filePath = dest.absolutePath,
            extractedText = extracted,
            addedAt = System.currentTimeMillis(),
        )
        // Copy succeeded; the file is only reachable through a project_documents row. If
        // insert or touch throws (project deleted mid-import, disk-full, cancellation) the
        // dest must go with it — otherwise it sits unreferenced until the whole project
        // folder is swept, and the files UI cannot remove it.
        try {
            projectDocumentDao.insert(document)
            touch(projectId)
        } catch (t: Throwable) {
            runCatching { projectDocumentDao.delete(document.id) }
            runCatching { dest.delete() }
            if (t is CancellationException) throw t
            return@withContext null
        }
        document
    }

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
        val docs = projectDocumentDao.getForProjectSync(projectId).filter { it.hasText }
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

    /**
     * Extract text for context injection. v1 reads text-shaped formats only (text MIME types, and a
     * few textual application types); binary formats like PDF keep a null body — they still list in
     * the project, they just contribute no context, and the UI says so. Extraction is capped per
     * document so one huge file can't dominate later assembly.
     */
    private fun extractText(file: File, mimeType: String, name: String): String? {
        val lower = mimeType.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        val looksTextual = lower.startsWith("text/") ||
            lower in TEXTUAL_MIME_TYPES ||
            ext in TEXTUAL_EXTENSIONS
        if (!looksTextual) return null
        // Read at most MAX_EXTRACT_CHARS characters so peak allocation is capped regardless of file
        // size — readText() would materialize the whole (possibly hundreds of MB) file first, then
        // truncate, risking OutOfMemoryError on large log/csv/sql files selected through SAF.
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(MAX_EXTRACT_CHARS)
                var filled = 0
                while (filled < buffer.size) {
                    val read = reader.read(buffer, filled, buffer.size - filled)
                    if (read < 0) break
                    filled += read
                }
                if (filled <= 0) null else String(buffer, 0, filled)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        /** Fallback when the user supplies a blank project name. */
        const val UNTITLED_PROJECT = "Untitled project"

        /** Hard cap on an imported file's size (bytes) — larger selections are rejected. */
        private const val MAX_IMPORT_BYTES = 25L * 1024 * 1024

        /** Per-document extraction cap on import (characters read into memory). */
        private const val MAX_EXTRACT_CHARS = 200_000

        /** Combined document budget injected into any single system prompt. */
        private const val MAX_DOC_CONTEXT_CHARS = 24_000

        private val TEXTUAL_MIME_TYPES = setOf(
            "application/json", "application/xml", "application/xhtml+xml",
            "application/javascript", "application/x-yaml", "application/yaml",
            "application/markdown", "application/x-sh", "application/csv",
        )

        private val TEXTUAL_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "xml", "yaml", "yml", "csv", "tsv",
            "html", "htm", "css", "js", "ts", "kt", "java", "py", "rb", "go",
            "rs", "c", "cpp", "h", "sh", "toml", "ini", "cfg", "log", "sql",
        )
    }
}
