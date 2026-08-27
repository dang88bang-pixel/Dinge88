package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.local.SqlCipherHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup/Restore der lokalen Room-Datenbank (SQLite-Datei).
 *
 * Backups landen im app-externen Dateibereich und können z. B. per
 * USB/ADB oder Dateimanager exportiert werden. Ein Restore ist nur bei
 * geschlossener Datenbank konsistent – daher wird die Datei zunächst
 * validiert und erst nach App-Neustart wirksam.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SecureGuardDatabase,
    private val databaseKeyManager: com.secureguard.enterprise.security.DatabaseKeyManager
) {

    private val dbFile: File
        get() = File(database.openHelper.writableDatabase.path)

    /**
     * Backup-Ordner. `getExternalFilesDir` kann null liefern (z. B. im
     * Direct-Boot-Modus oder bei fehlendem externem Speicher) – dann wird auf
     * den app-internen Speicher ausgewichen, damit kein NPE entsteht.
     */
    private val backupDir: File
        get() {
            val external = context.getExternalFilesDir(null)
            val base = external ?: context.filesDir
            return File(base, "backups").apply { mkdirs() }
        }

    /**
     * Kopiert die aktuelle Datenbank in den Backup-Ordner.
     * Vor dem Kopieren wird ein WAL-Checkpoint (TRUNCATE) ausgeführt, damit die
     * -wal-Datei geleert ist und die Kopie alle committeten Transaktionen enthält.
     */
    suspend fun createBackup(name: String = "secureguard_backup"): File {
        runCatching {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
        val source = dbFile
        val target = File(backupDir, "${name}_${System.currentTimeMillis()}.db")
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    /**
     * Stellt ein Backup wieder her. Die Datei wird hart validiert (Plain-SQLite
     * **oder** mit dem aktuellen SQLCipher-Key öffnbar); der eigentliche
     * Austausch passiert über [restorePending] beim nächsten Start, um
     * Room-Konsistenz zu wahren.
     */
    suspend fun stageRestore(backupFile: File): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!backupFile.exists() || backupFile.length() < 512L) return@withContext false
            if (!isValidSqlite(backupFile)) return@withContext false
            val staged = File(backupDir, "restore_pending.db")
            backupFile.copyTo(staged, overwrite = true)
            true
        }

    /**
     * Führt ein zuvor gestagtes Restore aus (im Application.onCreate).
     * Robust gegen Direct-Boot: `getExternalFilesDir` liefert hier ggf. null –
     * [backupDir] weicht dann auf `filesDir` aus, zusätzlich schützt
     * `runCatching` vor jedem Startup-Crash.
     */
    fun applyPendingRestoreIfPresent() {
        val staged = runCatching { File(backupDir, "restore_pending.db") }.getOrNull()
            ?: return
        if (!staged.exists()) return
        runCatching {
            val target = dbFile
            target.parentFile?.mkdirs()
            copyFile(staged, target)
            staged.delete()
        }
    }

    fun listBackups(): List<File> =
        backupDir.listFiles { f -> f.extension == "db" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private fun isValidSqlite(file: File): Boolean {
        // 1) Plain SQLite: Header direkt prüfbar.
        if (SqlCipherHelper.isPlainSqlite(file)) return true
        // 2) SQLCipher: alles, was kein Plain-Header ist, war vorher jede
        //    beliebige Datei → zusätzlich verlangen, dass die Datei sich mit
        //    dem aktuellen Passphrase als SQLCipher-DB öffnen lässt.
        return runCatching {
            SqlCipherHelper.validateSqlCipherFile(file, currentPassphrase())
        }.getOrDefault(false)
    }

    /** Liest die aktuelle SQLCipher-Passphrase (KeyStore-wrapped). */
    private fun currentPassphrase(): ByteArray =
        databaseKeyManager.getOrCreatePassphrase()

    private fun copyFile(source: File, target: File) {
        FileChannel.open(source.toPath()).use { src ->
            FileChannel.open(target.toPath(), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING).use { dst ->
                src.transferTo(0, src.size(), dst)
            }
        }
    }
}
