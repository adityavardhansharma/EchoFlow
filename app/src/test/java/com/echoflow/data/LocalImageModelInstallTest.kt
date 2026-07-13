package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class LocalImageModelInstallTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase
    private lateinit var dao: LocalImageModelDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.localImageModelDao()
        LocalImageDownloadFiles.root(context).deleteRecursively()
    }

    @After
    fun tearDown() {
        database.close()
        LocalImageDownloadFiles.root(context).deleteRecursively()
        SettingsRepository(context).saveLocalImageModel("")
    }

    private fun mediaEntry(zip: File): LocalImageCatalogEntry =
        LocalImageModelCatalog.entries.first().copy(
            artifactUrl = "https://example.invalid/model.zip",
            downloadBytes = zip.length(),
            installedBytes = 64,
            artifactRevision = "test-revision",
            artifactSha256 = LocalImageBundleValidator.sha256Of(zip),
        )

    private fun buildZip(
        entryName: String = "weights.bin",
        content: ByteArray = ByteArray(16) { it.toByte() },
    ): File {
        val zip = File.createTempFile("image-model", ".zip", context.cacheDir)
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry(entryName))
            output.write(content)
            output.closeEntry()
        }
        return zip
    }

    private fun installer(
        targetDao: LocalImageModelDao = dao,
        smokeTest: suspend (File) -> Unit = {},
    ) = LocalImageModelInstaller(context, targetDao, smokeTest)

    @Test
    fun `MediaPipe root archive installs under bins and persists runtime`() = runBlocking {
        val zip = buildZip()
        val entry = mediaEntry(zip)
        val model = installer().install(entry, zip, "test")

        val finalDir = LocalImageDownloadFiles.finalDirectory(context, entry)
        assertTrue(File(finalDir, "bins/weights.bin").isFile)
        assertEquals(LocalImageRuntime.MEDIAPIPE.id, model.runtime)
        assertNull(model.modelFileName)
        assertNotNull(dao.getById(entry.id))
    }

    @Test
    fun `unsafe path and excessive expanded size are rejected`() {
        val traversal = buildZip("../outside.bin")
        try {
            LocalImageBundleValidator.extractZipSafely(traversal, File(context.cacheDir, "unsafe"), 1024)
            fail("expected unsafe-path rejection")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("unsafe"))
        }

        val oversized = buildZip(content = ByteArray(128))
        try {
            LocalImageBundleValidator.extractZipSafely(oversized, File(context.cacheDir, "oversized"), 32)
            fail("expected expansion-limit rejection")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("expected size"))
        }
    }

    @Test
    fun `cancellation during extraction removes the installing directory`() = runBlocking {
        val zip = buildZip(content = ByteArray(512 * 1024))
        val entry = mediaEntry(zip).copy(installedBytes = 512 * 1024)
        var checks = 0
        try {
            installer().install(
                entry = entry,
                artifact = zip,
                ownerId = "cancelled",
                cancellationCheck = {
                    checks++
                    if (checks > 3) throw CancellationException("cancel")
                },
            )
            fail("expected cancellation")
        } catch (_: CancellationException) {
            // expected
        }
        assertFalse(LocalImageDownloadFiles.installingDirectory(context, entry, "cancelled").exists())
        assertNull(dao.getById(entry.id))
    }

    @Test
    fun `failed smoke test never replaces an existing working install`() = runBlocking {
        val firstZip = buildZip(content = "old".toByteArray())
        val entry = mediaEntry(firstZip)
        installer().install(entry, firstZip, "first")
        val existingFile = File(LocalImageDownloadFiles.finalDirectory(context, entry), "bins/weights.bin")
        assertEquals("old", existingFile.readText())

        val replacement = buildZip(content = "new".toByteArray())
        try {
            installer(smokeTest = { throw IOException("smoke failed") })
                .install(mediaEntry(replacement), replacement, "replacement")
            fail("expected smoke failure")
        } catch (_: IOException) {
            // expected
        }
        assertEquals("old", existingFile.readText())
        assertNotNull(dao.getById(entry.id))
    }

    @Test
    fun `Room failure rolls directory swap back to previous install`() = runBlocking {
        val zip = buildZip(content = "new".toByteArray())
        val entry = mediaEntry(zip)
        val finalDir = LocalImageDownloadFiles.finalDirectory(context, entry)
        File(finalDir, "bins").mkdirs()
        File(finalDir, "bins/weights.bin").writeText("old")

        val failingDao = object : LocalImageModelDao {
            override fun getAll(): Flow<List<LocalImageModel>> = flowOf(emptyList())
            override suspend fun getAllSync(): List<LocalImageModel> = emptyList()
            override suspend fun getById(id: String): LocalImageModel? = null
            override suspend fun upsert(model: LocalImageModel) = throw IOException("db failed")
            override suspend fun delete(id: String) = Unit
        }
        try {
            installer(failingDao).install(entry, zip, "rollback")
            fail("expected database failure")
        } catch (_: IOException) {
            // expected
        }
        assertEquals("old", File(finalDir, "bins/weights.bin").readText())
        assertFalse(LocalImageDownloadFiles.previousDirectory(context, entry).exists())
    }

    @Test
    fun `startup recovery restores previous directory after crash before Room commit`() = runBlocking {
        val oldZip = buildZip(content = "old".toByteArray())
        val oldEntry = mediaEntry(oldZip)
        installer().install(oldEntry, oldZip, "old")
        val finalDir = LocalImageDownloadFiles.finalDirectory(context, oldEntry)
        val previousDir = LocalImageDownloadFiles.previousDirectory(context, oldEntry)
        assertTrue(finalDir.renameTo(previousDir))

        val newZip = buildZip(content = "new".toByteArray())
        val newEntry = mediaEntry(newZip)
        File(finalDir, "bins").mkdirs()
        File(finalDir, "bins/weights.bin").writeText("new")
        LocalImageInstallMarker.write(finalDir, newEntry)
        // Deliberately do not upsert newEntry: this is the exact process-death window.

        LocalImageInstallRecovery.reconcile(context, dao)

        assertEquals("old", File(finalDir, "bins/weights.bin").readText())
        assertFalse(previousDir.exists())
        assertEquals(oldEntry.artifactSha256, dao.getById(oldEntry.id)?.bundleSha256)
    }

    @Test
    fun `startup recovery adopts validated first install marker without redownload`() = runBlocking {
        val header = "{\"weights\":{}}".toByteArray()
        // Recovery deliberately adopts only the artifact fingerprint pinned by the current
        // catalog. The marker proves the real Worker already verified the full artifact before
        // its atomic rename; this tiny file only stands in for the installed payload.
        val entry = LocalImageModelCatalog.entryById("local-image/dreamshaper-8")!!
        val finalDir = LocalImageDownloadFiles.finalDirectory(context, entry)
        val modelFile = File(finalDir, entry.modelFileName!!).apply {
            parentFile?.mkdirs()
            outputStream().use { output ->
                output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(header.size.toLong()).array())
                output.write(header)
                output.write(ByteArray(32))
            }
        }
        LocalImageInstallMarker.write(finalDir, entry)

        LocalImageInstallRecovery.reconcile(context, dao)

        val adopted = dao.getById(entry.id)
        assertNotNull(adopted)
        assertEquals(entry.artifactSha256, adopted!!.bundleSha256)
        assertTrue(modelFile.isFile)
        assertFalse(LocalImageDownloadFiles.partialFile(context, entry).exists())
    }

    @Test
    fun `startup recovery keeps an installed model removed from a later catalog`() = runBlocking {
        val model = LocalImageModel(
            id = "local-image/retired-model",
            name = "Retired model",
            directoryName = "retired-model",
            installedBytes = 3,
            sourceRevision = "old-revision",
            sourceCheckpointSha256 = "a".repeat(64),
            bundleSha256 = "b".repeat(64),
            licenseId = "test-license",
            activationPhrase = null,
            defaultNegativePrompt = null,
            bundleFormatVersion = 1,
            runtime = LocalImageRuntime.MEDIAPIPE.id,
            modelFileName = null,
            addedAt = 1L,
        )
        dao.upsert(model)
        val previous = File(LocalImageDownloadFiles.root(context), "retired-model.previous")
        File(previous, "bins/weights.bin").apply {
            parentFile?.mkdirs()
            writeText("old")
        }

        LocalImageInstallRecovery.reconcile(context, dao)

        assertEquals("old", File(LocalImageDownloadFiles.root(context), "retired-model/bins/weights.bin").readText())
        assertFalse(previous.exists())
        assertNotNull(dao.getById(model.id))
    }

    @Test
    fun `selection recovery falls back only to an actually installed row`() = runBlocking {
        val zip = buildZip()
        val entry = mediaEntry(zip)
        val installed = installer().install(entry, zip, "selected")
        val settings = SettingsRepository(context)
        settings.saveLocalImageModel("local-image/missing")

        LocalImageSelectionRecovery.reconcile(context, listOf(installed))
        assertEquals(installed.id, SettingsRepository(context).getLocalImageModelDirect())

        LocalImageSelectionRecovery.reconcile(context, emptyList())
        assertEquals("", SettingsRepository(context).getLocalImageModelDirect())
    }

    @Test
    fun `safetensors artifact installs as one cpp model file without native smoke test`() = runBlocking {
        val header = "{\"weights\":{}}".toByteArray()
        val modelFile = File.createTempFile("model", ".part", context.cacheDir)
        modelFile.outputStream().use { output ->
            output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(header.size.toLong()).array())
            output.write(header)
            output.write(ByteArray(32))
        }
        val base = LocalImageModelCatalog.entryById("local-image/dreamshaper-8")!!
        val entry = base.copy(
            artifactUrl = "https://example.invalid/model.safetensors",
            downloadBytes = modelFile.length(),
            installedBytes = modelFile.length(),
            artifactRevision = "test",
            artifactSha256 = LocalImageBundleValidator.sha256Of(modelFile),
        )
        var smokeCalls = 0
        val installed = installer(smokeTest = { smokeCalls++ }).install(entry, modelFile, "raw")

        assertEquals(0, smokeCalls)
        assertEquals(LocalImageRuntime.STABLE_DIFFUSION_CPP.id, installed.runtime)
        assertEquals(entry.modelFileName, installed.modelFileName)
        assertTrue(File(LocalImageDownloadFiles.finalDirectory(context, entry), entry.modelFileName!!).isFile)
    }
}
