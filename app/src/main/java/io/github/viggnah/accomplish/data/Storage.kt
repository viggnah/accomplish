package io.github.viggnah.accomplish.data

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

data class Volume(val label: String, val root: File, val removable: Boolean)

sealed interface LoadResult {
    data class Ok(val data: AppData, val note: String? = null) : LoadResult
    /** The folder's volume (usually the SD card) isn't there. Never fall back silently. */
    data class Missing(val dir: File) : LoadResult
    data class Failed(val message: String) : LoadResult
}

/**
 * Keeps data.json in a visible folder (normally on the SD card) so it survives an uninstall.
 * Writes go to a temp file that is synced and renamed over the real one, and a dated copy
 * is kept in backups/ for the last few days.
 */
class Storage(private val context: Context) {
    private val writer = Executors.newSingleThreadExecutor()
    private val pending = AtomicReference<Pair<File, AppData>?>(null)

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun volumes(): List<Volume> {
        val sm = context.getSystemService(StorageManager::class.java)
        return sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            if (v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
            Volume(v.getDescription(context), dir, v.isRemovable)
        }.sortedByDescending { it.removable }
    }

    fun isAvailable(dir: File): Boolean {
        val root = volumeRootOf(dir) ?: return false
        return Environment.getExternalStorageState(root) == Environment.MEDIA_MOUNTED && root.canWrite()
    }

    private fun volumeRootOf(dir: File): File? {
        val sm = context.getSystemService(StorageManager::class.java)
        return sm.getStorageVolume(dir)?.directory
    }

    fun load(dir: File): LoadResult {
        if (!isAvailable(dir)) return LoadResult.Missing(dir)
        if (!dir.exists() && !dir.mkdirs()) return LoadResult.Failed("Couldn't create ${dir.path}. Check that file access is allowed.")
        val file = File(dir, FILE)
        if (!file.exists()) return LoadResult.Ok(AppData(), note = null)
        return try {
            LoadResult.Ok(Json.decode(file.readText()))
        } catch (e: Exception) {
            // The live file is damaged; fall back to the newest readable backup and say so.
            val backup = backups(dir).firstNotNullOfOrNull { b -> runCatching { b to Json.decode(b.readText()) }.getOrNull() }
            if (backup != null) LoadResult.Ok(backup.second, note = "Your data file was damaged, so the backup from ${backup.first.nameWithoutExtension.removePrefix("data-")} was loaded.")
            else LoadResult.Failed("Your data file couldn't be read and no backup worked. The file is untouched at ${file.path}.")
        }
    }

    /** Queue a save; rapid changes collapse into one write of the latest data. */
    fun save(dir: File, data: AppData, keepBackups: Int) {
        if (pending.getAndSet(dir to data) != null) return
        writer.execute {
            val (d, latest) = pending.getAndSet(null) ?: return@execute
            runCatching { writeNow(d, latest, keepBackups) }
        }
    }

    fun flush() {
        val latch = java.util.concurrent.CountDownLatch(1)
        writer.execute { latch.countDown() }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun writeNow(dir: File, data: AppData, keepBackups: Int) {
        if (!isAvailable(dir)) return
        dir.mkdirs()
        val file = File(dir, FILE)
        val tmp = File(dir, "$FILE.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(Json.encode(data).toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        val bdir = File(dir, "backups").apply { mkdirs() }
        val today = File(bdir, "data-${LocalDate.now()}.json")
        if (!today.exists()) file.copyTo(today)
        backups(dir).drop(keepBackups).forEach { it.delete() }
    }

    private fun backups(dir: File): List<File> =
        File(dir, "backups").listFiles { f -> f.name.startsWith("data-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name } ?: emptyList()

    companion object {
        const val FILE = "data.json"
        const val FOLDER = "Accomplish"
        /** The folder's name before the app was called Accomplish. */
        const val OLD_FOLDER = "LifeOrganizer"

        /** Renames an old LifeOrganizer folder on [root] to Accomplish, unless an Accomplish folder is already there. */
        fun renameOldFolder(root: File): Boolean {
            val old = File(root, OLD_FOLDER)
            val new = File(root, FOLDER)
            return old.isDirectory && !new.exists() && old.renameTo(new)
        }
    }
}
