package com.echoflow.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LocalImageDownloadState {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : LocalImageDownloadState {
        val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }
    data object Verifying : LocalImageDownloadState
    data object Installing : LocalImageDownloadState
    data object Done : LocalImageDownloadState
    data class Failed(val message: String) : LocalImageDownloadState
}

/** Stable on-disk paths shared by the scheduler, worker and startup reconciliation. */
internal object LocalImageDownloadFiles {
    fun root(context: Context): File = File(context.filesDir, "image_models").apply { mkdirs() }

    fun downloads(context: Context): File = File(root(context), ".downloads").apply { mkdirs() }

    fun finalDirectory(context: Context, entry: LocalImageCatalogEntry): File =
        File(root(context), LocalImageModelCatalog.directoryNameFor(entry.id))

    fun previousDirectory(context: Context, entry: LocalImageCatalogEntry): File =
        File(root(context), "${LocalImageModelCatalog.directoryNameFor(entry.id)}.previous")

    fun installingDirectory(context: Context, entry: LocalImageCatalogEntry, ownerId: String): File =
        File(root(context), "${LocalImageModelCatalog.directoryNameFor(entry.id)}.installing.$ownerId")

    fun partialFile(context: Context, entry: LocalImageCatalogEntry): File {
        val extension = when (entry.artifactFormat) {
            LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP -> "zip"
            LocalImageArtifactFormat.SAFETENSORS -> "safetensors"
            LocalImageArtifactFormat.CHECKPOINT -> "ckpt"
        }
        val revision = entry.artifactSha256.orEmpty().take(12)
        val slug = LocalImageModelCatalog.directoryNameFor(entry.id)
        return File(downloads(context), "$slug-$revision.$extension.part")
    }

    fun partialFilesFor(context: Context, modelId: String): List<File> {
        val prefix = LocalImageModelCatalog.directoryNameFor(modelId) + "-"
        return downloads(context).listFiles().orEmpty().filter {
            it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".part")
        }
    }

    fun cancellationMarker(context: Context, modelId: String): File =
        File(downloads(context), "${LocalImageModelCatalog.directoryNameFor(modelId)}.cancelled")

    fun revisionPartialFile(
        context: Context,
        modelId: String,
        artifactSha256: String,
        modelFileName: String,
    ): File {
        val extension = modelFileName.substringAfterLast('.', "model")
        val slug = LocalImageModelCatalog.directoryNameFor(modelId)
        return File(downloads(context), "$slug-${artifactSha256.take(12)}.$extension.part")
    }
}

/** Serializes final-directory swaps, recovery and deletion across activity graph instances. */
internal object LocalImageInstallLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withModelLock(modelId: String, block: suspend () -> T): T =
        locks.getOrPut(modelId) { Mutex() }.withLock { block() }
}

/**
 * Fingerprint moved atomically with an installing directory. Room matching this marker is the
 * durable commit record; a mismatch means the process died after the rename but before upsert.
 */
internal object LocalImageInstallMarker {
    private const val FILE_NAME = ".echoflow-install"

    data class Fingerprint(
        val modelId: String,
        val artifactSha256: String,
        val runtime: String,
        val modelFileName: String?,
    )

    fun write(directory: File, entry: LocalImageCatalogEntry) {
        File(directory, FILE_NAME).writeText(
            listOf(
                entry.id,
                entry.artifactSha256.orEmpty(),
                entry.runtime.id,
                entry.modelFileName.orEmpty(),
            ).joinToString("\n")
        )
    }

    fun exists(directory: File): Boolean = File(directory, FILE_NAME).isFile

    fun read(directory: File): Fingerprint? {
        val lines = runCatching { File(directory, FILE_NAME).readLines() }.getOrNull() ?: return null
        return Fingerprint(
            modelId = lines.getOrNull(0) ?: return null,
            artifactSha256 = lines.getOrNull(1) ?: return null,
            runtime = lines.getOrNull(2) ?: return null,
            modelFileName = lines.getOrNull(3)?.ifBlank { null },
        )
    }

    fun matches(directory: File, model: LocalImageModel?): Boolean {
        if (model == null) return false
        val fingerprint = read(directory) ?: return false
        return fingerprint.modelId == model.id &&
            fingerprint.artifactSha256 == model.bundleSha256 &&
            fingerprint.runtime == model.runtime &&
            fingerprint.modelFileName == model.modelFileName
    }

    fun matches(directory: File, entry: LocalImageCatalogEntry): Boolean {
        val fingerprint = read(directory) ?: return false
        return fingerprint.modelId == entry.id &&
            fingerprint.artifactSha256 == entry.artifactSha256 &&
            fingerprint.runtime == entry.runtime.id &&
            fingerprint.modelFileName == entry.modelFileName
    }
}

internal object LocalImageInstalledModelFiles {
    fun isInstalled(directory: File, runtime: String, modelFileName: String?): Boolean =
        when (LocalImageRuntime.fromId(runtime)) {
            LocalImageRuntime.MEDIAPIPE -> File(directory, "bins").let { bins ->
                bins.isDirectory && bins.walkTopDown().any { it.isFile && it.length() > 0L }
            }
            LocalImageRuntime.STABLE_DIFFUSION_CPP ->
                modelFileName?.let { File(directory, it) }?.let { it.isFile && it.length() > 0L } == true
        }
}

internal object LocalImageModelRecords {
    fun fromInstalledDirectory(entry: LocalImageCatalogEntry, directory: File): LocalImageModel =
        LocalImageModel(
            id = entry.id,
            name = entry.name,
            directoryName = LocalImageModelCatalog.directoryNameFor(entry.id),
            installedBytes = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            sourceRevision = entry.artifactRevision.orEmpty(),
            sourceCheckpointSha256 = entry.artifactSha256.orEmpty(),
            bundleSha256 = entry.artifactSha256.orEmpty(),
            licenseId = entry.licenseId,
            activationPhrase = entry.activationPhrase,
            defaultNegativePrompt = entry.defaultNegativePrompt,
            bundleFormatVersion = entry.bundleFormatVersion,
            runtime = entry.runtime.id,
            modelFileName = entry.modelFileName,
            addedAt = System.currentTimeMillis(),
        )
}

internal object LocalImageSelectionRecovery {
    fun reconcile(context: Context, actuallyInstalled: List<LocalImageModel>) {
        val settings = SettingsRepository(context)
        val selectedId = settings.getLocalImageModelDirect()
        if (selectedId.isNotBlank() && actuallyInstalled.none { it.id == selectedId }) {
            settings.saveLocalImageModel(actuallyInstalled.firstOrNull()?.id.orEmpty())
        }
    }
}

/** Pure validation helpers; cancellation is injected so ZIP and hash loops remain testable. */
internal object LocalImageBundleValidator {
    private const val DEFAULT_MAX_ENTRIES = 10_000
    private const val MAX_SAFETENSORS_HEADER_BYTES = 64L * 1024 * 1024

    fun sha256Of(file: File, cancellationCheck: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                cancellationCheck()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun extractZipSafely(
        zip: File,
        targetDir: File,
        maxExtractedBytes: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        cancellationCheck: () -> Unit = {},
    ) {
        require(maxExtractedBytes > 0) { "maxExtractedBytes must be positive" }
        targetDir.mkdirs()
        val targetRoot = targetDir.canonicalFile
        var extractedBytes = 0L
        var entryCount = 0

        ZipFile(zip).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                cancellationCheck()
                if (++entryCount > maxEntries) {
                    throw IOException("The model archive contains too many files.")
                }
                val entry = entries.nextElement()
                val normalized = entry.name.replace('\\', '/')
                val pathParts = normalized.split('/')
                if (normalized.startsWith('/') || normalized.contains(':') || pathParts.any { it == ".." }) {
                    throw IOException("The model archive contains an unsafe path.")
                }
                if (entry.size > maxExtractedBytes ||
                    (entry.size >= 0 && entry.size > maxExtractedBytes - extractedBytes)
                ) {
                    throw IOException("The model archive expands beyond its expected size.")
                }

                val output = File(targetDir, normalized)
                val canonical = output.canonicalFile
                if (canonical != targetRoot && !canonical.path.startsWith(targetRoot.path + File.separator)) {
                    throw IOException("The model archive contains an unsafe path.")
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                    continue
                }

                output.parentFile?.mkdirs()
                archive.getInputStream(entry).use { input ->
                    output.outputStream().use { sink ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            cancellationCheck()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read.toLong() > maxExtractedBytes - extractedBytes) {
                                throw IOException("The model archive expands beyond its expected size.")
                            }
                            sink.write(buffer, 0, read)
                            extractedBytes += read
                        }
                    }
                }
            }
        }

        targetDir.walkTopDown().filter { it != targetDir }.forEach { file ->
            cancellationCheck()
            val expected = File(file.parentFile?.canonicalFile, file.name)
            if (file.canonicalFile.path != expected.path) {
                throw IOException("The model archive contains a symbolic link.")
            }
        }
    }

    fun validateMediaPipeDirectory(binsDir: File) {
        if (!binsDir.isDirectory || binsDir.walkTopDown().none { it.isFile && it.length() > 0L }) {
            throw IOException("The model archive contains no model files.")
        }
    }

    fun validateSingleModelFile(file: File, format: LocalImageArtifactFormat) {
        if (!file.isFile || file.length() < 16L) throw IOException("The downloaded model file is empty.")
        RandomAccessFile(file, "r").use { source ->
            when (format) {
                LocalImageArtifactFormat.SAFETENSORS -> {
                    val lengthBytes = ByteArray(8)
                    source.readFully(lengthBytes)
                    var headerLength = 0L
                    for (index in 0 until 8) {
                        headerLength = headerLength or
                            ((lengthBytes[index].toLong() and 0xffL) shl (index * 8))
                    }
                    if (headerLength !in 2..MAX_SAFETENSORS_HEADER_BYTES ||
                        headerLength > file.length() - 8
                    ) {
                        throw IOException("The downloaded safetensors header is invalid.")
                    }
                    if (source.readByte().toInt().toChar() != '{') {
                        throw IOException("The downloaded file is not a safetensors model.")
                    }
                }
                LocalImageArtifactFormat.CHECKPOINT -> {
                    val prefix = ByteArray(4)
                    source.readFully(prefix)
                    val zipCheckpoint = prefix.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
                    val pickleCheckpoint = prefix[0] == 0x80.toByte() &&
                        (prefix[1].toInt() and 0xff) in 2..5
                    if (!zipCheckpoint && !pickleCheckpoint) {
                        throw IOException("The downloaded file is not a supported checkpoint.")
                    }
                }
                LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP ->
                    throw IOException("A ZIP archive cannot be installed as a single model file.")
            }
        }
    }
}

/**
 * Validates and commits already-downloaded artifacts. The final directory is changed only inside
 * a non-cancellable rename/Room transaction-like section, with a rollback directory preserving
 * the previously working install until the new row is durable.
 */
internal class LocalImageModelInstaller(
    private val context: Context,
    private val dao: LocalImageModelDao,
    private val smokeTest: suspend (binsDir: File) -> Unit,
) {
    suspend fun install(
        entry: LocalImageCatalogEntry,
        artifact: File,
        ownerId: String,
        onState: suspend (LocalImageDownloadState) -> Unit = {},
        cancellationCheck: () -> Unit = {},
        installingDirectory: File = LocalImageDownloadFiles.installingDirectory(context, entry, ownerId),
    ): LocalImageModel {
        return when (entry.artifactFormat) {
            LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP ->
                installMediaPipe(entry, artifact, installingDirectory, onState, cancellationCheck)
            LocalImageArtifactFormat.SAFETENSORS,
            LocalImageArtifactFormat.CHECKPOINT ->
                installSingleFile(entry, artifact, installingDirectory, onState, cancellationCheck)
        }
    }

    private suspend fun installMediaPipe(
        entry: LocalImageCatalogEntry,
        artifact: File,
        installingDir: File,
        onState: suspend (LocalImageDownloadState) -> Unit,
        cancellationCheck: () -> Unit,
    ): LocalImageModel {
        onState(LocalImageDownloadState.Installing)
        installingDir.deleteRecursively()
        val bins = File(installingDir, "bins")
        val expected = entry.installedBytes ?: throw IOException("The model's installed size is unknown.")
        val extractionLimit = expected + expected / 4 + 64L * 1024 * 1024
        try {
            LocalImageBundleValidator.extractZipSafely(
                artifact,
                bins,
                maxExtractedBytes = extractionLimit,
                cancellationCheck = cancellationCheck,
            )
            LocalImageBundleValidator.validateMediaPipeDirectory(bins)
            smokeTest(bins)
            cancellationCheck()
            return commitDirectory(entry, installingDir)
        } catch (error: Throwable) {
            installingDir.deleteRecursively()
            throw error
        }
    }

    private suspend fun installSingleFile(
        entry: LocalImageCatalogEntry,
        artifact: File,
        installingDir: File,
        onState: suspend (LocalImageDownloadState) -> Unit,
        cancellationCheck: () -> Unit,
    ): LocalImageModel {
        onState(LocalImageDownloadState.Installing)
        LocalImageBundleValidator.validateSingleModelFile(artifact, entry.artifactFormat)
        cancellationCheck()
        val fileName = entry.modelFileName ?: throw IOException("The model filename is missing.")
        installingDir.deleteRecursively()
        installingDir.mkdirs()
        val preparedFile = File(installingDir, fileName)
        if (!artifact.renameTo(preparedFile)) {
            throw IOException("Couldn't prepare the downloaded model for installation.")
        }

        try {
            return commitDirectory(entry, installingDir) { newDirectory ->
                val installedFile = File(newDirectory, fileName)
                artifact.parentFile?.mkdirs()
                if (installedFile.exists() && !installedFile.renameTo(artifact)) {
                    throw IOException("Couldn't preserve the downloaded model during rollback.")
                }
            }
        } catch (error: Throwable) {
            if (preparedFile.exists() && !preparedFile.renameTo(artifact)) {
                error.addSuppressed(IOException("The verified model remains in ${installingDir.name}."))
            } else {
                installingDir.deleteRecursively()
            }
            throw error
        }
    }

    private suspend fun commitDirectory(
        entry: LocalImageCatalogEntry,
        installingDir: File,
        preserveNewOnRollback: (File) -> Unit = {},
    ): LocalImageModel = LocalImageInstallLocks.withModelLock(entry.id) {
        // Waiting for the per-model lock remains cancellable. Only the tiny filesystem/Room
        // commit below is non-cancellable, so a queued cancelled worker can never resurrect data.
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable + Dispatchers.IO) {
            val finalDir = LocalImageDownloadFiles.finalDirectory(context, entry)
            val previousDir = LocalImageDownloadFiles.previousDirectory(context, entry)
            LocalImageInstallMarker.write(installingDir, entry)

            // A retry may enter with both final and previous left by a killed Worker. Reconcile
            // against Room + marker before touching either directory.
            LocalImageInstallRecovery.reconcileEntryLocked(context, entry, dao.getById(entry.id))
            if (LocalImageDownloadFiles.cancellationMarker(context, entry.id).exists()) {
                throw CancellationException("Model installation was cancelled.")
            }

            var movedPrevious = false
            try {
                if (finalDir.exists()) {
                    if (!finalDir.renameTo(previousDir)) {
                        throw IOException("Couldn't prepare the existing model for an update.")
                    }
                    movedPrevious = true
                }
                if (!installingDir.renameTo(finalDir)) {
                    throw IOException("Couldn't finalize the model installation.")
                }

                val row = LocalImageModelRecords.fromInstalledDirectory(entry, finalDir)
                dao.upsert(row)
                // Failure to delete is safe: marker+Room prove final is committed and startup
                // reconciliation will retry removing the rollback copy.
                if (previousDir.exists()) previousDir.deleteRecursively()
                row
            } catch (error: Throwable) {
                val newDirectory = when {
                    finalDir.exists() -> finalDir
                    installingDir.exists() -> installingDir
                    else -> null
                }
                val preserveError = newDirectory?.let {
                    runCatching { preserveNewOnRollback(it) }.exceptionOrNull()
                }
                preserveError?.let(error::addSuppressed)
                if (newDirectory != null && preserveError == null &&
                    newDirectory.exists() && !newDirectory.deleteRecursively()
                ) {
                    error.addSuppressed(IOException("The incomplete installation could not be removed."))
                }
                if (movedPrevious && previousDir.exists()) {
                    if (finalDir.exists()) {
                        error.addSuppressed(IOException("The previous model is safe in ${previousDir.name}, but final is still occupied."))
                    } else if (!previousDir.renameTo(finalDir)) {
                        error.addSuppressed(IOException("The previous model could not be restored."))
                    }
                }
                throw error
            }
        }
    }
}

/** Crash recovery for the rename-to-previous → rename-new → Room-upsert commit sequence. */
internal object LocalImageInstallRecovery {
    suspend fun reconcile(
        context: Context,
        dao: LocalImageModelDao,
        activeModelIds: Set<String> = emptySet(),
    ) = withContext(Dispatchers.IO) {
        val rows = dao.getAllSync().associateBy { it.id }
        val catalogEntries = LocalImageModelCatalog.entries.associateBy { it.id }
        catalogEntries.values.filterNot { it.id in activeModelIds }.forEach { entry ->
            LocalImageInstallLocks.withModelLock(entry.id) {
                val finalDir = LocalImageDownloadFiles.finalDirectory(context, entry)
                var row = rows[entry.id]
                if (row == null &&
                    LocalImageInstallMarker.matches(finalDir, entry) &&
                    LocalImageInstalledModelFiles.isInstalled(finalDir, entry.runtime.id, entry.modelFileName)
                ) {
                    row = LocalImageModelRecords.fromInstalledDirectory(entry, finalDir)
                    dao.upsert(row)
                }
                reconcileEntryLocked(context, entry, row)
            }
        }
        // Installed rows deliberately outlive the curated catalog. If a later app update removes
        // an entry while its install swap is interrupted, recover it from the persisted directory
        // name instead of dropping the row and stranding the known-good `.previous` directory.
        rows.values
            .filter { it.id !in catalogEntries && it.id !in activeModelIds }
            .forEach { row ->
                LocalImageInstallLocks.withModelLock(row.id) {
                    reconcilePathsLocked(context, row.name, row.directoryName, row)
                }
            }
    }

    internal fun reconcileEntryLocked(
        context: Context,
        entry: LocalImageCatalogEntry,
        row: LocalImageModel?,
    ) = reconcilePathsLocked(
        context = context,
        displayName = entry.name,
        directoryName = LocalImageModelCatalog.directoryNameFor(entry.id),
        row = row,
    )

    private fun reconcilePathsLocked(
        context: Context,
        displayName: String,
        directoryName: String,
        row: LocalImageModel?,
    ) {
        val finalDir = File(LocalImageDownloadFiles.root(context), directoryName)
        val previousDir = File(LocalImageDownloadFiles.root(context), "$directoryName.previous")

        when {
            previousDir.exists() && !finalDir.exists() -> {
                if (!previousDir.renameTo(finalDir)) {
                    throw IOException("Couldn't restore the previous $displayName installation.")
                }
            }
            previousDir.exists() && finalDir.exists() &&
                LocalImageInstallMarker.matches(finalDir, row) -> {
                // Room committed the new fingerprint; the rollback copy is obsolete.
                if (!previousDir.deleteRecursively()) {
                    throw IOException("Couldn't remove the old $displayName rollback copy.")
                }
            }
            previousDir.exists() && finalDir.exists() &&
                LocalImageInstallMarker.exists(finalDir) -> {
                preserveSingleFileArtifact(context, finalDir)
                if (!finalDir.deleteRecursively()) {
                    throw IOException("Couldn't remove the uncommitted $displayName installation.")
                }
                if (!previousDir.renameTo(finalDir)) {
                    throw IOException("Couldn't restore the previous $displayName installation.")
                }
            }
            previousDir.exists() && finalDir.exists() -> {
                // A pre-marker (v14) final is the only provably installed directory.
                if (!previousDir.deleteRecursively()) {
                    throw IOException("Couldn't remove a stale $displayName rollback copy.")
                }
            }
            finalDir.exists() && LocalImageInstallMarker.exists(finalDir) &&
                !LocalImageInstallMarker.matches(finalDir, row) -> {
                preserveSingleFileArtifact(context, finalDir)
                if (!finalDir.deleteRecursively()) {
                    throw IOException("Couldn't remove the uncommitted $displayName installation.")
                }
            }
        }
    }

    private fun preserveSingleFileArtifact(context: Context, directory: File) {
        val fingerprint = LocalImageInstallMarker.read(directory) ?: return
        if (fingerprint.runtime != LocalImageRuntime.STABLE_DIFFUSION_CPP.id) return
        val fileName = fingerprint.modelFileName ?: return
        val modelFile = File(directory, fileName)
        if (!modelFile.isFile) return
        val partial = LocalImageDownloadFiles.revisionPartialFile(
            context,
            fingerprint.modelId,
            fingerprint.artifactSha256,
            fileName,
        )
        partial.parentFile?.mkdirs()
        if (partial.exists() && !partial.delete()) {
            throw IOException("Couldn't replace the saved partial model during recovery.")
        }
        if (!modelFile.renameTo(partial)) {
            throw IOException("Couldn't preserve the verified model during recovery.")
        }
    }
}

/**
 * App-facing facade. WorkManager owns downloads; this class only enqueues unique work, translates
 * durable WorkInfo into the existing settings state, and provides installed-file paths.
 */
class LocalImageModelManager(
    private val context: Context,
    private val dao: LocalImageModelDao,
    private val smokeTest: suspend (binsDir: File) -> Unit = mediaPipeSmokeTest(context),
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val immediateFailures = MutableStateFlow<Map<String, LocalImageDownloadState.Failed>>(emptyMap())

    val states: StateFlow<Map<String, LocalImageDownloadState>> = combine(
        workInfosFlow(),
        immediateFailures.asStateFlow(),
    ) { workInfos, failures ->
        val latestByModel = workInfos
            .mapNotNull { info ->
                val modelId = LocalImageModelDownloadWorker.modelIdFromTags(info.tags)
                    ?: return@mapNotNull null
                Triple(modelId, LocalImageModelDownloadWorker.enqueuedAtFromTags(info.tags), info)
            }
            .groupBy { it.first }
            .mapValues { (_, candidates) -> candidates.maxBy { it.second }.third }

        buildMap {
            latestByModel.forEach { (id, info) ->
                workInfoState(info)?.let { put(id, it) }
            }
            // Local validation happens after any older WorkInfo was recorded. Keep that
            // actionable failure visible instead of letting a stale completed job replace it.
            failures.forEach { (id, state) -> put(id, state) }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // Reconcile at graph construction, not only when the user later opens Settings.
        scope.launch(Dispatchers.IO) { pruneOrphans() }
    }

    val imageModelsDir: File get() = LocalImageDownloadFiles.root(context)

    fun directoryFor(model: LocalImageModel): File = File(imageModelsDir, model.directoryName)

    fun binsDirFor(model: LocalImageModel): File = File(directoryFor(model), "bins")

    fun modelFileFor(model: LocalImageModel): File? =
        model.modelFileName?.let { File(directoryFor(model), it) }

    fun isInstalled(model: LocalImageModel): Boolean =
        LocalImageInstalledModelFiles.isInstalled(directoryFor(model), model.runtime, model.modelFileName)

    fun deviceMeetsRam(entry: LocalImageCatalogEntry): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        return info.totalMem <= 0 || info.totalMem >= entry.minRamBytes
    }

    fun deviceSupportsRuntime(entry: LocalImageCatalogEntry): Boolean =
        entry.runtime != LocalImageRuntime.STABLE_DIFFUSION_CPP ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun download(entry: LocalImageCatalogEntry) {
        if (!entry.artifactAvailable) {
            immediateFailures.value = immediateFailures.value +
                (entry.id to LocalImageDownloadState.Failed("This model isn't ready to download yet."))
            return
        }
        if (!deviceSupportsRuntime(entry)) {
            immediateFailures.value = immediateFailures.value +
                (entry.id to LocalImageDownloadState.Failed("Experimental image models require Android 8 or newer."))
            return
        }
        immediateFailures.value = immediateFailures.value - entry.id
        LocalImageDownloadFiles.cancellationMarker(context, entry.id).delete()
        val enqueuedAt = System.currentTimeMillis()
        val request = OneTimeWorkRequestBuilder<LocalImageModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    LocalImageModelDownloadWorker.KEY_MODEL_ID to entry.id,
                    LocalImageModelDownloadWorker.KEY_ENQUEUED_AT to enqueuedAt,
                    LocalImageModelDownloadWorker.KEY_TOTAL_BYTES to (entry.downloadBytes ?: 0L),
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(LocalImageModelDownloadWorker.WORK_TAG)
            .addTag(LocalImageModelDownloadWorker.modelTag(entry.id))
            .addTag(LocalImageModelDownloadWorker.enqueuedAtTag(enqueuedAt))
            .build()
        workManager.enqueueUniqueWork(
            LocalImageModelDownloadWorker.uniqueWorkName(entry.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(entryId: String) {
        immediateFailures.value = immediateFailures.value - entryId
        LocalImageDownloadFiles.cancellationMarker(context, entryId).apply {
            parentFile?.mkdirs()
            writeText("cancelled")
        }
        workManager.cancelUniqueWork(LocalImageModelDownloadWorker.uniqueWorkName(entryId))
    }

    fun clearFailed(entryId: String) {
        immediateFailures.value = immediateFailures.value - entryId
    }

    suspend fun delete(model: LocalImageModel) = withContext(Dispatchers.IO) {
        LocalImageDownloadFiles.cancellationMarker(context, model.id).apply {
            parentFile?.mkdirs()
            writeText("cancelled")
        }
        workManager.cancelUniqueWork(LocalImageModelDownloadWorker.uniqueWorkName(model.id))
        LocalImageInstallLocks.withModelLock(model.id) {
            val directory = directoryFor(model)
            if (directory.exists() && !directory.deleteRecursively()) {
                throw IOException("Couldn't delete ${model.name} from device storage.")
            }
            val previous = File(imageModelsDir, "${model.directoryName}.previous")
            if (previous.exists() && !previous.deleteRecursively()) {
                throw IOException("Couldn't delete ${model.name}'s rollback copy.")
            }
            dao.delete(model.id)
        }
        LocalImageDownloadFiles.partialFilesFor(context, model.id).forEach(File::delete)
        immediateFailures.value = immediateFailures.value - model.id
    }

    /** Compatibility/test seam for the MediaPipe archive installer. */
    internal suspend fun installDownloadedZip(
        entry: LocalImageCatalogEntry,
        zipFile: File,
        installingDir: File,
    ): LocalImageModel {
        require(entry.artifactFormat == LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP)
        val expectedSha = entry.artifactSha256 ?: throw IOException("This model isn't ready to download yet.")
        if (!LocalImageBundleValidator.sha256Of(zipFile).equals(expectedSha, ignoreCase = true)) {
            throw IOException("The downloaded file failed verification. Retry the download.")
        }
        return LocalImageModelInstaller(context, dao, smokeTest).install(
            entry = entry,
            artifact = zipFile,
            ownerId = "direct",
            installingDirectory = installingDir,
        )
    }

    /**
     * Reconcile completed installs without deleting resumable partials. Only abandoned
     * `.installing.<work-id>` directories whose WorkInfo is no longer active are removed.
     */
    suspend fun pruneOrphans() = withContext(Dispatchers.IO) {
        val activeWork = runCatching {
            workManager.getWorkInfosByTag(LocalImageModelDownloadWorker.WORK_TAG)
                .get(10, TimeUnit.SECONDS)
                .filter { !it.state.isFinished }
        }.getOrNull() ?: return@withContext
        val activeWorkIds = activeWork.map { it.id.toString() }.toSet()
        val activeModelIds = activeWork.mapNotNull {
            LocalImageModelDownloadWorker.modelIdFromTags(it.tags)
        }.toSet()

        imageModelsDir.listFiles().orEmpty().forEach { file ->
            if (file.isDirectory && ".installing." in file.name) {
                val ownerId = file.name.substringAfterLast(".installing.")
                if (ownerId !in activeWorkIds) file.deleteRecursively()
            }
        }
        LocalImageInstallRecovery.reconcile(context, dao, activeModelIds)
        dao.getAllSync().filterNot { it.id in activeModelIds }.forEach { model ->
            LocalImageInstallLocks.withModelLock(model.id) {
                if (!isInstalled(model)) dao.delete(model.id)
            }
        }

        val installedRows = dao.getAllSync().filter(::isInstalled)
        LocalImageSelectionRecovery.reconcile(context, installedRows)
    }

    private fun workInfoState(info: WorkInfo): LocalImageDownloadState? {
        val phase = info.progress.getString(LocalImageModelDownloadWorker.KEY_PHASE)
        val downloaded = info.progress.getLong(LocalImageModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
        val total = info.progress.getLong(
            LocalImageModelDownloadWorker.KEY_TOTAL_BYTES,
            LocalImageModelDownloadWorker.modelIdFromTags(info.tags)
                ?.let(LocalImageModelCatalog::entryById)
                ?.downloadBytes ?: 0L,
        )
        return when (info.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> LocalImageDownloadState.Downloading(downloaded, total)
            WorkInfo.State.RUNNING -> when (phase) {
                LocalImageModelDownloadWorker.PHASE_VERIFYING -> LocalImageDownloadState.Verifying
                LocalImageModelDownloadWorker.PHASE_INSTALLING -> LocalImageDownloadState.Installing
                else -> LocalImageDownloadState.Downloading(downloaded, total)
            }
            WorkInfo.State.SUCCEEDED -> LocalImageDownloadState.Done
            WorkInfo.State.FAILED -> LocalImageDownloadState.Failed(
                info.outputData.getString(LocalImageModelDownloadWorker.KEY_ERROR)
                    ?: "Download failed."
            )
            WorkInfo.State.CANCELLED -> null
        }
    }

    private fun workInfosFlow() = callbackFlow<List<WorkInfo>> {
        val liveData = workManager.getWorkInfosByTagLiveData(LocalImageModelDownloadWorker.WORK_TAG)
        val observer = Observer<List<WorkInfo>> { infos -> trySend(infos.orEmpty()) }
        withContext(Dispatchers.Main.immediate) { liveData.observeForever(observer) }
        awaitClose {
            val mainLooper = Looper.getMainLooper()
            if (Looper.myLooper() == mainLooper) {
                liveData.removeObserver(observer)
            } else {
                Handler(mainLooper).post { liveData.removeObserver(observer) }
            }
        }
    }

    companion object {
        fun mediaPipeSmokeTest(context: Context): suspend (File) -> Unit = { binsDir ->
            withContext(Dispatchers.Default) {
                val options = ImageGenerator.ImageGeneratorOptions.builder()
                    .setImageGeneratorModelDirectory(binsDir.absolutePath)
                    .build()
                ImageGenerator.createFromOptions(context, options).close()
            }
        }
    }
}
