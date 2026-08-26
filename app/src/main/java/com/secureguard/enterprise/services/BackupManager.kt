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
    private val database: SecureGuardDatabase
) {

    private val dbFile: File
        get() = File(database.openHelper.writableDatabase.path)

    private val backupDir: File
        get() = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    /** Kopiert die aktuelle Datenbank in den Backup-Ordner. */
    suspend fun createBackup(name: String = "secureguard_backup"): File {
        val source = dbFile
        val target = File(backupDir, "${name}_${System.currentTimeMillis()}.db")
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    /**
     * Stellt ein Backup wieder her. Die Datei wird auf ein gültiges
     * SQLite-Header-Signatur geprüft; der eigentliche Austausch passiert
     * über [restorePending] beim nächsten Start, um Room-Konsistenz zu wahren.
     */
    suspend fun stageRestore(backupFile: File): Boolean {
        if (!backupFile.exists() || !isValidSqlite(backupFile)) return false
        val staged = File(backupDir, "restore_pending.db")
        backupFile.copyTo(staged, overwrite = true)
        return true
    }

    /** Führt ein zuvor gestagtes Restore aus (im Application.onCreate). */
    fun applyPendingRestoreIfPresent() {
        val staged = File(backupDir, "restore_pending.db")
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
        // Plain SQLite ODER SQLCipher (kein Klartext-Header)
        return SqlCipherHelper.isPlainSqlite(file) || SqlCipherHelper.isSqlCipherDatabase(file)
    }

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
