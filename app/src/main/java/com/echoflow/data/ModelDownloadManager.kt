package com.echoflow.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    data class Downloading(val bytes: Long, val total: Long) : DownloadState() {
        val fraction: Float get() = if (total > 0) bytes.toFloat() / total else 0f
    }
    data class Failed(val message: String) : DownloadState()
    object Done : DownloadState()
}

/**
 * Downloads curated models from HuggingFace and imports user-picked model files into
 * filesDir/models. Runs on its own scope so an in-flight download survives leaving the
 * Settings screen (not process death — partial files are pruned on next start).
 */
class ModelDownloadManager(
    private val context: Context,
    private val localModelDao: LocalModelDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Downloads stream for many minutes; readTimeout only bounds gaps between packets.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jobs = mutableMapOf<String, Job>()
    private val activeDownloads = mutableMapOf<String, CatalogEntry>()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun fileFor(model: LocalModel): File = File(modelsDir, model.fileName)

    private fun setState(id: String, state: DownloadState?) {
        _states.value = _states.value.toMutableMap().apply {
            if (state == null) remove(id) else put(id, state)
        }
    }

    fun download(entry: CatalogEntry, hfToken: String?) {
        if (jobs[entry.id]?.isActive == true) return
        activeDownloads[entry.id] = entry
        setState(entry.id, DownloadState.Downloading(0, entry.approxSizeBytes))

        // Keep the process alive while the multi-GB transfer streams, even minimized.
        KeepAliveService.acquire(context, "Downloading ${entry.name}…")
        jobs[entry.id] = scope.launch {
            val partFile = File(modelsDir, entry.fileName + ".part")
            val finalFile = File(modelsDir, entry.fileName)
            try {
                val builder = Request.Builder().url(entry.url)
                if (!hfToken.isNullOrBlank()) {
                    builder.addHeader("Authorization", "Bearer ${hfToken.trim()}")
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val message = when (response.code) {
                            401, 403 ->
                                if (entry.requiresAuth) {
                                    "This model is gated. Paste a HuggingFace access token in Settings " +
                                        "after accepting the model license on huggingface.co."
                                } else {
                                    "HuggingFace rejected the download (HTTP ${response.code})."
                                }
                            404 -> "Model file not found on HuggingFace — the repo may have renamed it."
                            else -> "Download failed (HTTP ${response.code})."
                        }
                        throw Exception(message)
                    }
                    val body = response.body ?: throw Exception("Empty download response.")
                    val total = body.contentLength().takeIf { it > 0 } ?: entry.approxSizeBytes
                    var copied = 0L
                    var lastReported = 0L
                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                // Throttle state updates to every ~2 MB to avoid flow churn.
                                if (copied - lastReported > 2L * 1024 * 1024) {
                                    lastReported = copied
                                    setState(entry.id, DownloadState.Downloading(copied, total))
                                }
                            }
                        }
                    }
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }
                    localModelDao.insertLocalModel(
                        LocalModel(
                            id = entry.id,
                            name = entry.name,
                            fileName = entry.fileName,
                            sizeBytes = finalFile.length(),
                            source = "curated",
                            addedAt = System.currentTimeMillis()
                        )
                    )
                    setState(entry.id, DownloadState.Done)
                    activeDownloads.remove(entry.id)
                }
            } catch (e: Exception) {
                partFile.delete()
                if (e is kotlinx.coroutines.CancellationException) {
                    setState(entry.id, null)
                    activeDownloads.remove(entry.id)
                    throw e
                }
                setState(entry.id, DownloadState.Failed(e.message ?: "Download failed."))
            }
        }
        jobs[entry.id]?.invokeOnCompletion { KeepAliveService.release(context) }
    }

    fun cancel(entryId: String) {
        jobs.remove(entryId)?.cancel()
        (activeDownloads[entryId] ?: LocalModelCatalog.entryById(entryId))?.let {
            File(modelsDir, it.fileName + ".part").delete()
        }
        activeDownloads.remove(entryId)
        setState(entryId, null)
    }

    /** Copies a user-picked .task/.litertlm file into app storage with progress. */
    suspend fun importFromUri(uri: Uri): LocalModel = withContext(Dispatchers.IO) {
        var displayName = "model.task"
        var declaredSize = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { displayName = it }
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) declaredSize = cursor.getLong(sizeIdx)
            }
        }

        val lower = displayName.lowercase()
        if (!lower.endsWith(".task") && !lower.endsWith(".litertlm")) {
            throw Exception("Unsupported file. Pick a .task or .litertlm model file.")
        }

        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val id = "local/imported-" + safeName.lowercase()
            .removeSuffix(".task").removeSuffix(".litertlm")
        val finalFile = File(modelsDir, safeName)
        val partFile = File(modelsDir, "$safeName.part")

        setState(id, DownloadState.Downloading(0, declaredSize))
        KeepAliveService.acquire(context, "Importing $displayName…")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Could not open the selected file.")
            var copied = 0L
            var lastReported = 0L
            input.use { source ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - lastReported > 2L * 1024 * 1024) {
                            lastReported = copied
                            setState(id, DownloadState.Downloading(copied, declaredSize))
                        }
                    }
                }
            }
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            val model = LocalModel(
                id = id,
                name = displayName.removeSuffix(".task").removeSuffix(".litertlm"),
                fileName = safeName,
                sizeBytes = finalFile.length(),
                source = "imported",
                addedAt = System.currentTimeMillis()
            )
            localModelDao.insertLocalModel(model)
            setState(id, DownloadState.Done)
            model
        } catch (e: Exception) {
            partFile.delete()
            setState(id, if (e is kotlinx.coroutines.CancellationException) null else DownloadState.Failed(e.message ?: "Import failed."))
            throw e
        } finally {
            KeepAliveService.release(context)
        }
    }

    suspend fun delete(model: LocalModel) = withContext(Dispatchers.IO) {
        fileFor(model).delete()
        localModelDao.deleteLocalModel(model.id)
        setState(model.id, null)
    }

    /**
     * Reconciles app storage and DB after upgrades or DB resets that keep files but lose
     * rows: rows whose file vanished are dropped, and model files on disk with no row are
     * re-adopted (matched back to catalog entries by file name when possible) so they show
     * up as installed without re-downloading.
     */
    suspend fun pruneOrphans() = withContext(Dispatchers.IO) {
        val existingModels = localModelDao.getAllLocalModelsSync()
        val (tracked, missing) = existingModels.partition { fileFor(it).exists() }
        missing.forEach { localModelDao.deleteLocalModel(it.id) }
        val trackedFileNames = tracked.map { it.fileName.lowercase() }.toSet()

        modelsDir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(".part") -> file.delete()
                // Already represented by a DB row — don't create a duplicate "recovered" entry.
                file.name.lowercase() in trackedFileNames -> Unit
                file.isFile &&
                    (file.name.endsWith(".task", ignoreCase = true) ||
                        file.name.endsWith(".litertlm", ignoreCase = true)) -> {
                    val catalogEntry = LocalModelCatalog.entries.firstOrNull { it.fileName == file.name }
                    val safeId = catalogEntry?.id ?: "local/recovered-" + file.name.lowercase()
                        .removeSuffix(".task")
                        .removeSuffix(".litertlm")
                        .replace(Regex("[^a-z0-9]+"), "-")
                        .trim('-')
                    val displayName = catalogEntry?.name ?: file.name
                        .removeSuffix(".task")
                        .removeSuffix(".litertlm")
                        .replace('_', ' ')
                        .replace('-', ' ')
                    localModelDao.insertLocalModel(
                        LocalModel(
                            id = safeId,
                            name = displayName,
                            fileName = file.name,
                            sizeBytes = file.length(),
                            source = catalogEntry?.let { "curated" } ?: "recovered",
                            addedAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun clearFailed(entryId: String) = setState(entryId, null)
}
