package com.echoflow.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

class HuggingFaceModelSearch {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val resultType = Types.newParameterizedType(List::class.java, HfModel::class.java)
    private val resultAdapter = moshi.adapter<List<HfModel>>(resultType)

    suspend fun search(query: String, hfToken: String?): List<CatalogEntry> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifEmpty { "qwen3 gemma" }
        val url = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addQueryParameter("author", "litert-community")
            .addQueryParameter("search", cleanQuery)
            .addQueryParameter("limit", "30")
            .addQueryParameter("full", "true")
            .build()

        val request = Request.Builder().url(url)
            .apply {
                if (!hfToken.isNullOrBlank()) addHeader("Authorization", "Bearer ${hfToken.trim()}")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Hugging Face search failed (HTTP ${response.code}).")
            val body = response.body?.string().orEmpty()
            val models = resultAdapter.fromJson(body).orEmpty()
            models.mapNotNull { it.toCatalogEntry(hfToken) }
                .distinctBy { it.id }
                .sortedWith(compareBy<CatalogEntry> { it.approxSizeBytes <= 0L }.thenBy { it.approxSizeBytes })
                .take(12)
        }
    }

    private fun HfModel.toCatalogEntry(hfToken: String?): CatalogEntry? {
        val repoId = modelId ?: id ?: return null
        if (pipelineTag != null && pipelineTag != "text-generation") return null
        val lowerTags = tags.orEmpty().map { it.lowercase(Locale.US) }
        if (libraryName != "litert-lm" && "litertlm" !in lowerTags && "litert-lm" !in lowerTags) return null

        val file = siblings.orEmpty()
            .map { it.rfilename }
            .filter { it.endsWith(".litertlm", ignoreCase = true) || it.endsWith(".task", ignoreCase = true) }
            .filterNot { it.contains("mediatek", ignoreCase = true) || it.contains("qualcomm", ignoreCase = true) || it.contains("intel", ignoreCase = true) || it.contains("google_tensor", ignoreCase = true) }
            .sortedWith(compareBy<String> { !it.contains("mixed_int4", ignoreCase = true) }
                .thenBy { !it.endsWith(".litertlm", ignoreCase = true) }
                .thenBy { it.length })
            .firstOrNull() ?: return null

        val size = resolveSize(repoId, file, hfToken).takeIf { it > 0L } ?: usedStorage ?: 0L
        val requiresAuth = gated == true || gated is String
        val family = repoId.substringAfterLast("/")
        val slug = (repoId.substringAfter("/") + "-" + file.substringBeforeLast("."))
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        return CatalogEntry(
            id = "local/hf-$slug",
            name = family.replace('-', ' ').replace('_', ' '),
            fileName = file.substringAfterLast("/"),
            url = "https://huggingface.co/$repoId/resolve/main/$file",
            approxSizeBytes = size,
            requiresAuth = requiresAuth,
            maxTokens = if (file.contains("4096")) 4096 else 2048,
            description = "Hugging Face mobile bundle from $repoId. File: ${file.substringAfterLast("/")}"
        )
    }

    private fun resolveSize(repoId: String, fileName: String, hfToken: String?): Long {
        val request = Request.Builder()
            .head()
            .url("https://huggingface.co/$repoId/resolve/main/$fileName")
            .apply {
                if (!hfToken.isNullOrBlank()) addHeader("Authorization", "Bearer ${hfToken.trim()}")
            }
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.header("X-Linked-Size")?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()
                    ?: 0L
            }
        }.getOrDefault(0L)
    }

}

private data class HfModel(
    val id: String? = null,
    val modelId: String? = null,
    val gated: Any? = null,
    val tags: List<String>? = null,
    @Json(name = "pipeline_tag") val pipelineTag: String? = null,
    @Json(name = "library_name") val libraryName: String? = null,
    val siblings: List<HfSibling>? = null,
    val usedStorage: Long? = null,
)

private data class HfSibling(
    val rfilename: String,
)
