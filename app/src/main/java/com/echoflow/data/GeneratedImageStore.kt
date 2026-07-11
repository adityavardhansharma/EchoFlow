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
        val base64 = dataUrl.substringAfter("base64,", dataUrl)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        imagesDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val file = File(imagesDir, "$id.png")
        file.writeBytes(bytes)
        val image = GeneratedImage(
            id = id,
            chatId = chatId,
            filePath = file.absolutePath,
            prompt = prompt,
            parentId = parentId,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(image)
        image
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
