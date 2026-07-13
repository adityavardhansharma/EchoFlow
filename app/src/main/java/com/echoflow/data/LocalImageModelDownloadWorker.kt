package com.echoflow.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.echoflow.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class RetryableModelDownloadException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

internal class PermanentModelDownloadException(message: String) : IOException(message)

/** HTTP downloader with strict Content-Range validation and resumable `.part` files. */
internal class ResumableArtifactDownloader(
    private val client: OkHttpClient,
) {
    suspend fun download(
        entry: LocalImageCatalogEntry,
        partFile: File,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val url = entry.artifactUrl ?: throw PermanentModelDownloadException("The model URL is missing.")
        val expectedBytes = entry.downloadBytes
            ?: throw PermanentModelDownloadException("The model size is unknown.")
        partFile.parentFile?.mkdirs()
        if (partFile.length() > expectedBytes) partFile.delete()

        var existingBytes = partFile.length()
        onProgress(existingBytes, expectedBytes)
        val request = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "EchoFlow-Android")
            .apply { if (existingBytes > 0L) header("Range", "bytes=$existingBytes-") }
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw RetryableModelDownloadException("Connection lost. The download will resume.", error)
        }

        response.use {
            when {
                it.code == 416 && existingBytes == expectedBytes -> return@withContext
                it.code == 416 -> {
                    partFile.delete()
                    throw RetryableModelDownloadException("The server rejected the saved download position; retrying from the start.")
                }
                it.code == 408 || it.code == 429 || it.code >= 500 ->
                    throw RetryableModelDownloadException("The model host is temporarily unavailable (HTTP ${it.code}).")
                it.code == 401 || it.code == 403 ->
                    throw PermanentModelDownloadException("The model host denied this download (HTTP ${it.code}).")
                it.code == 404 ->
                    throw PermanentModelDownloadException("The model file is no longer available.")
                !it.isSuccessful ->
                    throw PermanentModelDownloadException("Download failed (HTTP ${it.code}).")
            }

            val append = if (existingBytes > 0L && it.code == 206) {
                val contentRange = parseContentRange(it.header("Content-Range"))
                    ?: run {
                        partFile.delete()
                        throw RetryableModelDownloadException("The server returned an invalid resume response.")
                    }
                if (contentRange.first != existingBytes || contentRange.third != expectedBytes) {
                    partFile.delete()
                    throw RetryableModelDownloadException("The model changed while it was downloading; retrying from the start.")
                }
                true
            } else {
                // A server may ignore Range and return the full file. Truncate instead of
                // appending, otherwise the final hash could never match.
                existingBytes = 0L
                false
            }

            val body = it.body ?: throw RetryableModelDownloadException("The model host returned an empty response.")
            var copied = existingBytes
            var lastReportedBytes = copied
            var lastReportedAt = System.nanoTime()
            try {
                body.byteStream().use { input ->
                    FileOutputStream(partFile, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (copied > expectedBytes) {
                                partFile.delete()
                                throw PermanentModelDownloadException("The downloaded file is larger than expected.")
                            }
                            val now = System.nanoTime()
                            if (copied - lastReportedBytes >= PROGRESS_BYTES ||
                                now - lastReportedAt >= PROGRESS_NANOS
                            ) {
                                lastReportedBytes = copied
                                lastReportedAt = now
                                onProgress(copied, expectedBytes)
                            }
                        }
                        output.flush()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: PermanentModelDownloadException) {
                throw error
            } catch (error: IOException) {
                throw RetryableModelDownloadException("Connection lost. The download will resume.", error)
            }

            onProgress(copied, expectedBytes)
            if (copied != expectedBytes) {
                throw RetryableModelDownloadException("The download was interrupted and will resume.")
            }
        }
    }

    /** Returns start, end and total. */
    private fun parseContentRange(value: String?): Triple<Long, Long, Long>? {
        val match = CONTENT_RANGE.matchEntire(value.orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (start < 0 || end < start || total <= end) return null
        return Triple(start, end, total)
    }

    companion object {
        private const val PROGRESS_BYTES = 8L * 1024 * 1024
        private const val PROGRESS_NANOS = 750L * 1_000_000
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}

/**
 * Persistent owner of model download, verification and installation. WorkManager recreates this
 * worker after process death/reboot; the revision-named `.part` file lets HTTP continue by Range.
 */
class LocalImageModelDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext failure("The scheduled model id is missing.")
        val entry = LocalImageModelCatalog.entryById(modelId)
            ?: return@withContext failure("This model is no longer in the catalog.")
        if (!entry.artifactAvailable) return@withContext failure("This model isn't ready to download yet.")
        if (entry.runtime == LocalImageRuntime.STABLE_DIFFUSION_CPP &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        ) {
            return@withContext failure("Experimental image models require Android 8 or newer.")
        }
        if (LocalImageDownloadFiles.cancellationMarker(applicationContext, entry.id).exists()) {
            return@withContext failure("The model download was cancelled.")
        }

        val partFile = LocalImageDownloadFiles.partialFile(applicationContext, entry)
        val installingDir = LocalImageDownloadFiles.installingDirectory(
            applicationContext,
            entry,
            id.toString(),
        )
        val expectedBytes = entry.downloadBytes ?: return@withContext failure("The model size is unknown.")

        try {
            recoverCompletedInstall(entry)?.let {
                LocalImageDownloadFiles.partialFile(applicationContext, entry).delete()
                selectFirstInstall(entry)
                LocalImageDownloadFiles.cancellationMarker(applicationContext, entry.id).delete()
                return@withContext Result.success(workDataOf(KEY_MODEL_ID to entry.id))
            }
            currentCoroutineContext().ensureActive()
            if (LocalImageDownloadFiles.cancellationMarker(applicationContext, entry.id).exists()) {
                throw CancellationException("Model download was cancelled.")
            }
            ensureFreeSpace(entry, partFile)
            publish(PHASE_DOWNLOADING, partFile.length(), expectedBytes, entry.name)
            ResumableArtifactDownloader(client).download(entry, partFile) { downloaded, total ->
                publish(PHASE_DOWNLOADING, downloaded, total, entry.name)
            }

            publish(PHASE_VERIFYING, partFile.length(), expectedBytes, entry.name)
            val coroutineContext = currentCoroutineContext()
            val actualHash = LocalImageBundleValidator.sha256Of(partFile) {
                coroutineContext.ensureActive()
            }
            if (!actualHash.equals(entry.artifactSha256, ignoreCase = true)) {
                partFile.delete()
                return@withContext failure("The downloaded file failed verification. Retry the download.")
            }

            val installer = LocalImageModelInstaller(
                applicationContext,
                database.localImageModelDao(),
                LocalImageModelManager.mediaPipeSmokeTest(applicationContext),
            )
            installer.install(
                entry = entry,
                artifact = partFile,
                ownerId = id.toString(),
                installingDirectory = installingDir,
                onState = {
                    publish(PHASE_INSTALLING, expectedBytes, expectedBytes, entry.name)
                },
                cancellationCheck = { coroutineContext.ensureActive() },
            )
            partFile.delete()

            // Do this in the worker rather than the UI so the first install is selected even
            // when it finishes after the activity has been closed or the process was recreated.
            selectFirstInstall(entry)
            LocalImageDownloadFiles.cancellationMarker(applicationContext, entry.id).delete()
            Result.success(workDataOf(KEY_MODEL_ID to entry.id))
        } catch (error: CancellationException) {
            installingDir.deleteRecursively()
            throw error
        } catch (error: RetryableModelDownloadException) {
            installingDir.deleteRecursively()
            Result.retry()
        } catch (error: Exception) {
            installingDir.deleteRecursively()
            failure(error.message ?: "Download failed.")
        }
    }

    private suspend fun recoverCompletedInstall(entry: LocalImageCatalogEntry): LocalImageModel? =
        LocalImageInstallLocks.withModelLock(entry.id) {
            val dao = database.localImageModelDao()
            val finalDir = LocalImageDownloadFiles.finalDirectory(applicationContext, entry)
            var row = dao.getById(entry.id)

            // Marker is written only after hash/format validation (and MediaPipe smoke test),
            // so a killed first install can safely finish its Room commit without redownloading.
            if (row == null &&
                LocalImageInstallMarker.matches(finalDir, entry) &&
                LocalImageInstalledModelFiles.isInstalled(finalDir, entry.runtime.id, entry.modelFileName)
            ) {
                row = LocalImageModelRecords.fromInstalledDirectory(entry, finalDir)
                dao.upsert(row)
            } else {
                LocalImageInstallRecovery.reconcileEntryLocked(applicationContext, entry, row)
            }

            row?.takeIf {
                it.bundleSha256.equals(entry.artifactSha256, ignoreCase = true) &&
                    it.runtime == entry.runtime.id &&
                    it.modelFileName == entry.modelFileName &&
                    LocalImageInstalledModelFiles.isInstalled(finalDir, it.runtime, it.modelFileName) &&
                    (!LocalImageInstallMarker.exists(finalDir) || LocalImageInstallMarker.matches(finalDir, it))
            }
        }

    private fun selectFirstInstall(entry: LocalImageCatalogEntry) {
        SettingsRepository(applicationContext).let { settings ->
            if (settings.getLocalImageModelDirect().isBlank()) {
                settings.saveLocalImageModel(entry.id)
            }
        }
    }

    private fun ensureFreeSpace(entry: LocalImageCatalogEntry, partFile: File) {
        val total = entry.downloadBytes ?: 0L
        val remainingDownload = (total - partFile.length()).coerceAtLeast(0L)
        val installOverhead = when (entry.artifactFormat) {
            LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP -> entry.installedBytes ?: total
            LocalImageArtifactFormat.SAFETENSORS,
            LocalImageArtifactFormat.CHECKPOINT -> 64L * 1024 * 1024
        }
        val safetyMargin = 128L * 1024 * 1024
        val required = remainingDownload + installOverhead + safetyMargin
        val available = StatFs(LocalImageDownloadFiles.root(applicationContext).absolutePath).availableBytes
        if (available < required) {
            throw PermanentModelDownloadException("Not enough free storage to download and install this model.")
        }
    }

    private suspend fun publish(phase: String, downloaded: Long, total: Long, name: String) {
        setProgress(
            workDataOf(
                KEY_PHASE to phase,
                KEY_DOWNLOADED_BYTES to downloaded,
                KEY_TOTAL_BYTES to total,
            )
        )
        setForeground(createForegroundInfo(phase, downloaded, total, name))
    }

    private fun createForegroundInfo(
        phase: String,
        downloaded: Long,
        total: Long,
        name: String,
    ): ForegroundInfo {
        createNotificationChannel()
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val phaseText = when (phase) {
            PHASE_VERIFYING -> "Verifying $name"
            PHASE_INSTALLING -> "Installing $name"
            else -> "Downloading $name"
        }
        val progress = if (total > 0L) ((downloaded.coerceIn(0L, total) * 100L) / total).toInt() else 0
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("On-device image model")
            .setContentText(phaseText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, total <= 0L || phase != PHASE_DOWNLOADING)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val notificationId = NOTIFICATION_ID_BASE + (id.hashCode() and 0x0fff)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "On-device model download and installation progress" }
        )
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR to message))

    companion object {
        const val WORK_TAG = "local-image-model-download"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_ENQUEUED_AT = "enqueued_at"
        const val KEY_PHASE = "phase"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_VERIFYING = "verifying"
        const val PHASE_INSTALLING = "installing"

        private const val NOTIFICATION_CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID_BASE = 4_200
        private const val MODEL_TAG_PREFIX = "local-image-model:"
        private const val ENQUEUED_TAG_PREFIX = "local-image-enqueued:"

        fun uniqueWorkName(modelId: String): String = "local-image-download:$modelId"
        fun modelTag(modelId: String): String = MODEL_TAG_PREFIX + modelId
        fun enqueuedAtTag(timestamp: Long): String = ENQUEUED_TAG_PREFIX + timestamp
        fun modelIdFromTags(tags: Set<String>): String? =
            tags.firstOrNull { it.startsWith(MODEL_TAG_PREFIX) }?.removePrefix(MODEL_TAG_PREFIX)
        fun enqueuedAtFromTags(tags: Set<String>): Long =
            tags.firstOrNull { it.startsWith(ENQUEUED_TAG_PREFIX) }
                ?.removePrefix(ENQUEUED_TAG_PREFIX)
                ?.toLongOrNull() ?: 0L
    }
}
