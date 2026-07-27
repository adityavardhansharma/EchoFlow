package com.echoflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reclaims the disk that on-device image generation left behind when it was removed.
 *
 * Dropping `local_image_models` in the v17 migration removes the install bookkeeping, but the
 * bundles themselves were files under filesDir/image_models/ — several GB for a user who had
 * downloaded a couple of models. Room migrations cannot touch the filesystem, so the sweep
 * happens once on the first launch after the upgrade.
 *
 * Guarded by a preference rather than by the directory's absence: a user who never installed
 * a model has no directory to find, and re-checking on every launch would be a pointless
 * disk hit on the startup path.
 */
object LegacyImageModelCleanup {
    private const val KEY_DONE = "legacy_image_models_swept"

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        val prefs = SettingsPreferenceStorage.legacy(context)
        if (prefs.getBoolean(KEY_DONE, false)) return@withContext

        // Marked done regardless of the outcome: a directory that cannot be deleted now will
        // not become deletable later, and retrying forever would just repeat the cost.
        runCatching { File(context.filesDir, "image_models").deleteRecursively() }
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }
}
