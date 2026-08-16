package com.echoflow.data

/**
 * The on-device backup format (see [BackupManager]).
 *
 * Everything here is durable, re-creatable-value data: conversations, the user's keys and
 * settings, and Echo Labs profiles / model directory rows. Deliberately excluded are large,
 * re-downloadable/re-creatable *files* (downloaded local models, generated images and videos)
 * and transient rows (browser sessions, research runs) — their rows are skipped so a restore
 * never points at a file that no longer exists.
 */

/** One persisted preference, tagged with its type so a restore writes it back correctly. */
data class BackupSetting(
    val key: String,
    val type: String, // "string" | "bool" | "int" | "long" | "float" | "stringSet"
    val value: String? = null,     // scalar values, stringified
    val set: List<String>? = null, // members, for "stringSet"
)

/** The full backup payload, serialized to JSON and then encrypted. */
data class BackupBundle(
    val schemaVersion: Int,
    val appVersionName: String,
    val createdAt: Long,
    val settings: List<BackupSetting>,
    val threads: List<ChatThread>,
    val messages: List<ChatMessage>,
    val advisorProfiles: List<AdvisorProfile>,
    val fusionPanels: List<FusionPanel>,
    val agentProfiles: List<AgentProfile>,
    val customModels: List<CustomModel>,
    val deepResearchModels: List<DeepResearchModel>,
    val imageModels: List<ImageModel>,
    val videoModels: List<VideoModel>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Outcome of a restore attempt, surfaced to the UI. */
sealed interface BackupResult {
    /** Restore succeeded; the caller should restart so every cached value is re-read. */
    data object Success : BackupResult

    /** Decryption failed — almost always the wrong passkey. */
    data object WrongKey : BackupResult

    /** The file was missing, unreadable, or not an EchoFlow backup. */
    data class Error(val message: String) : BackupResult
}
