package com.echoflow.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns generated-image persistence: decodes the model's base64 payload to a PNG file under
 * filesDir/generated_images/ and records the durable [GeneratedImage] row. The database only
 * ever stores file paths — image bytes never enter Room or segmentsJson.
 */
class GeneratedImageStore(
    context: Context,
    private val dao: GeneratedImageDao,
) {
    private val imagesDir = File(context.filesDir, "generated_images")

    /**
     * Decodes [dataUrl] (a `data:image/...;base64,...` URL or bare base64) to disk and inserts
     * the version row. [parentId] links an edit to the version it revised.
     */
    suspend fun save(
        chatId: String,
        prompt: String,
        dataUrl: String,
        parentId: String?,
    ): GeneratedImage = withContext(Dispatchers.IO) {
        require(dataUrl.length <= 32 * 1024 * 1024) { "Generated image exceeds the 24 MB limit." }
        val base64 = dataUrl.substringAfter("base64,", dataUrl)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        imagesDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val file = File(imagesDir, "$id.png")
        val temp = File(imagesDir, "$id.png.tmp")
        val image = GeneratedImage(
            id = id,
            chatId = chatId,
            filePath = file.absolutePath,
            prompt = prompt,
            parentId = parentId,
            createdAt = System.currentTimeMillis(),
        )
        try {
            require(bytes.isNotEmpty()) { "The generated image was empty." }
            temp.writeBytes(bytes)
            check(temp.renameTo(file)) { "Could not save the generated image." }
            dao.insert(image)
            image
        } catch (e: Exception) {
            temp.delete()
            file.delete()
            throw e
        }
    }

    /**
     * Persists an on-device generation result. The PNG is compressed to a temporary file
     * and atomically renamed, and the Room row is only inserted once the final file exists —
     * a failed compression leaves neither a file nor a row behind.
     */
    suspend fun saveBitmap(
        chatId: String,
        prompt: String,
        bitmap: android.graphics.Bitmap,
        parentId: String?,
    ): GeneratedImage = withContext(Dispatchers.IO) {
        imagesDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val finalFile = File(imagesDir, "$id.png")
        val tempFile = File(imagesDir, "$id.png.tmp")
        try {
            tempFile.outputStream().use { out ->
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) {
                    throw Exception("Could not encode the generated image.")
                }
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            finalFile.delete()
            throw e
        }
        val image = GeneratedImage(
            id = id,
            chatId = chatId,
            filePath = finalFile.absolutePath,
            prompt = prompt,
            parentId = parentId,
            createdAt = System.currentTimeMillis(),
        )
        try {
            dao.insert(image)
            image
        } catch (e: Exception) {
            finalFile.delete()
            throw e
        }
    }

    /**
     * Adopts a PNG produced by the isolated native image process. Moving a file path keeps
     * the full-resolution bitmap out of Binder and avoids decoding/re-encoding it in the
     * main process. The source must be an app-private pending file.
     */
    suspend fun savePngFile(
        chatId: String,
        prompt: String,
        pendingFile: File,
        parentId: String?,
    ): GeneratedImage = withContext(Dispatchers.IO) {
        val canonicalPending = pendingFile.canonicalFile
        val allowedRoot = File(imagesDir, ".native_pending").canonicalFile
        require(
            canonicalPending.parentFile == allowedRoot &&
                canonicalPending.isFile &&
                canonicalPending.extension.equals("png", ignoreCase = true)
        ) { "The native image result was invalid." }

        imagesDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val finalFile = File(imagesDir, "$id.png")
        try {
            if (!canonicalPending.renameTo(finalFile)) {
                canonicalPending.copyTo(finalFile, overwrite = false)
                canonicalPending.delete()
            }
            val image = GeneratedImage(
                id = id,
                chatId = chatId,
                filePath = finalFile.absolutePath,
                prompt = prompt,
                parentId = parentId,
                createdAt = System.currentTimeMillis(),
            )
            dao.insert(image)
            image
        } catch (e: Exception) {
            canonicalPending.delete()
            finalFile.delete()
            throw e
        }
    }

    /** The newest version in this chat — the image a follow-up edit turn revises. */
    suspend fun latestForChat(chatId: String): GeneratedImage? {
        val latest = dao.getLatestForChat(chatId) ?: return null
        return latest.takeIf { File(it.filePath).exists() }
    }

    /**
     * Removes this chat's image files from disk. Must run BEFORE the chat thread row is
     * deleted — the generated_images rows cascade away with it, and after that the PNGs
     * would be unreachable orphans growing app storage forever.
     */
    suspend fun deleteFilesForChat(chatId: String) = withContext(Dispatchers.IO) {
        dao.getForChat(chatId).forEach { image ->
            runCatching { File(image.filePath).delete() }
        }
    }

    /** The stored file re-encoded as a data URL for an edit request, or null if missing. */
    suspend fun asDataUrl(image: GeneratedImage): String? = withContext(Dispatchers.IO) {
        val file = File(image.filePath)
        if (!file.exists()) return@withContext null
        "data:image/png;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }
}
