package com.echoflow.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The on-device, uninstall-surviving backup (Privacy page).
 *
 * The encrypted file is written to public Downloads/EchoFlow so it outlives an uninstall
 * (app-private storage, and the Keystore key that protects the in-app prefs, are both wiped when
 * the app is removed). Only the user's passkey can open it — see [BackupCrypto]. On a fresh
 * install the user re-enters that passkey on the Recover screen and picks the file via the system
 * document picker, so no persisted permission is needed to read it back.
 *
 * Scope is text data only (chats, keys/settings, profiles, model directory rows); large,
 * re-creatable files (downloaded models, generated media) are not backed up.
 */
class BackupManager(
    private val appContext: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val bundleAdapter = moshi.adapter(BackupBundle::class.java)
    private val envelopeAdapter = moshi.adapter(BackupEnvelope::class.java)

    // ── Public API ─────────────────────────────────────────────────────────────────────

    /** Re-write the backup only when the feature is on and a passkey exists. Best-effort. */
    suspend fun exportIfEnabled() {
        if (settings.getBackupEnabledDirect() && settings.hasBackupPasskey()) {
            runCatching { export() }
        }
    }

    /** Build, encrypt, and write the backup file using the stored passkey. */
    suspend fun export(): Boolean = withContext(Dispatchers.IO) {
        val passkey = settings.getBackupPasskeyDirect()
        if (passkey.isBlank()) return@withContext false
        val bytes = encodeToBytes(buildBundle(), passkey)
        writeBackupFile(bytes)
    }

    /** Read [uri], decrypt with [passkey], and restore its contents. */
    suspend fun restore(uri: Uri, passkey: String): BackupResult = withContext(Dispatchers.IO) {
        val fileBytes = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext BackupResult.Error("Couldn't read that file.")

        val bundle = try {
            decodeFromBytes(fileBytes, passkey)
        } catch (e: javax.crypto.AEADBadTagException) {
            return@withContext BackupResult.WrongKey
        } catch (e: Exception) {
            // A bad-tag can also surface wrapped; treat auth failures as a wrong key.
            if (e.isAuthFailure()) return@withContext BackupResult.WrongKey
            return@withContext BackupResult.Error("That doesn't look like an EchoFlow backup.")
        } ?: return@withContext BackupResult.Error("That doesn't look like an EchoFlow backup.")

        runCatching { applyBundle(bundle) }
            .fold(
                onSuccess = { BackupResult.Success },
                onFailure = { BackupResult.Error("Restore failed: ${it.message ?: "unknown error"}") },
            )
    }

    /** Remove the backup file (called when the feature is turned off). Best-effort. */
    suspend fun deleteBackupFile() = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                findBackupUri()?.let { appContext.contentResolver.delete(it, null, null) }
            } else {
                legacyBackupFile().takeIf { it.exists() }?.delete()
            }
        }
        Unit
    }

    // ── Codec (pure; unit-testable without Android IO) ───────────────────────────────────

    /** bundle → JSON → AES-GCM → envelope JSON bytes (the file body). */
    fun encodeToBytes(bundle: BackupBundle, passkey: String): ByteArray {
        val plaintext = bundleAdapter.toJson(bundle).toByteArray(Charsets.UTF_8)
        val envelope = BackupCrypto.encrypt(plaintext, passkey)
        return envelopeAdapter.toJson(envelope).toByteArray(Charsets.UTF_8)
    }

    /** Inverse of [encodeToBytes]. Throws [javax.crypto.AEADBadTagException] on a wrong passkey. */
    fun decodeFromBytes(fileBytes: ByteArray, passkey: String): BackupBundle? {
        val envelope = envelopeAdapter.fromJson(String(fileBytes, Charsets.UTF_8)) ?: return null
        if (envelope.magic != BackupCrypto.MAGIC) return null
        val plaintext = BackupCrypto.decrypt(envelope, passkey)
        return bundleAdapter.fromJson(String(plaintext, Charsets.UTF_8))
    }

    // ── Gather / apply ───────────────────────────────────────────────────────────────────

    suspend fun buildBundle(): BackupBundle = BackupBundle(
        schemaVersion = BackupBundle.SCHEMA_VERSION,
        appVersionName = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty(),
        createdAt = System.currentTimeMillis(),
        settings = settings.exportSettings(),
        threads = db.chatDao().getAllThreadsSync(),
        messages = db.messageDao().getAllMessagesSync(),
        advisorProfiles = db.advisorProfileDao().getAllSync(),
        fusionPanels = db.fusionPanelDao().getAllSync(),
        agentProfiles = db.agentProfileDao().getAllSync(),
        customModels = db.customModelDao().getAllCustomModelsSync(),
        deepResearchModels = db.deepResearchModelDao().getAllSync(),
        imageModels = db.imageModelDao().getAllSync(),
        videoModels = db.videoModelDao().getAllSync(),
    )

    suspend fun applyBundle(bundle: BackupBundle) {
        // Keys/settings first, then DB rows. Threads before messages for the FK.
        settings.importSettings(bundle.settings)
        db.withTransaction {
            bundle.threads.forEach { db.chatDao().insertThread(it) }
            bundle.messages.forEach { db.messageDao().insertMessage(it) }
            bundle.advisorProfiles.forEach { db.advisorProfileDao().insert(it) }
            bundle.fusionPanels.forEach { db.fusionPanelDao().upsert(it) }
            bundle.agentProfiles.forEach { db.agentProfileDao().insert(it) }
            bundle.customModels.forEach { db.customModelDao().insertCustomModel(it) }
            bundle.deepResearchModels.forEach { db.deepResearchModelDao().insert(it) }
            bundle.imageModels.forEach { db.imageModelDao().insert(it) }
            bundle.videoModels.forEach { db.videoModelDao().insert(it) }
        }
    }

    // ── File location (survives uninstall) ───────────────────────────────────────────────

    private fun writeBackupFile(bytes: ByteArray): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val target = findBackupUri() ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
                },
            ) ?: return@runCatching false
            resolver.openOutputStream(target, "wt")?.use { it.write(bytes) } ?: return@runCatching false
            true
        } else {
            val file = legacyBackupFile()
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        }
    }.getOrDefault(false)

    /** The existing backup's MediaStore Uri, if one is already in Downloads/EchoFlow (Q+). */
    private fun findBackupUri(): Uri? {
        val resolver = appContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf("%$SUBDIR%", FILE_NAME)
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build()
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun legacyBackupFile(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$SUBDIR/$FILE_NAME")

    private fun Throwable.isAuthFailure(): Boolean =
        this is javax.crypto.AEADBadTagException ||
            cause is javax.crypto.AEADBadTagException ||
            (message?.contains("tag", ignoreCase = true) == true)

    companion object {
        const val SUBDIR = "EchoFlow"
        const val FILE_NAME = "echoflow-backup.efbak"
    }
}
