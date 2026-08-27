package com.secureguard.enterprise.data.local

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream

/**
 * SQLCipher-Hilfen: native lib laden, Room-Factory, Migration plain → encrypted.
 *
 * Dependency: `net.zetetic:sqlcipher-android` (SQLCipher 4.x).
 */
object SqlCipherHelper {

    private const val TAG = "SqlCipherHelper"

    @Volatile
    private var loaded = false

    /** Lädt native SQLCipher-Bibliothek (idempotent). */
    @Synchronized
    fun loadNative(context: Context) {
        if (loaded) return
        SQLiteDatabase.loadLibs(context)
        loaded = true
        Log.i(TAG, "SQLCipher native libs geladen")
    }

    fun createFactory(context: Context, passphrase: ByteArray): SupportOpenHelperFactory {
        loadNative(context)
        return SupportOpenHelperFactory(passphrase.copyOf())
    }

    /**
     * Migriert eine bestehende unverschlüsselte SQLite-Datei nach SQLCipher.
     *
     * @return true wenn Migration durchgeführt wurde
     */
    fun migratePlainToEncryptedIfNeeded(
        context: Context,
        dbName: String,
        passphrase: ByteArray
    ): Boolean {
        loadNative(context)
        val dbPath = context.getDatabasePath(dbName)
        if (!dbPath.exists()) {
            Log.i(TAG, "Keine bestehende DB – SQLCipher startet frisch")
            return false
        }
        if (isSqlCipherDatabase(dbPath)) {
            Log.i(TAG, "DB bereits SQLCipher-verschlüsselt")
            return false
        }
        if (!isPlainSqlite(dbPath)) {
            Log.w(TAG, "Unbekanntes DB-Format – Migration übersprungen")
            return false
        }

        Log.i(TAG, "Migriere plain SQLite → SQLCipher …")
        val parent = dbPath.parentFile ?: context.filesDir
        val plainBackup = File(parent, "$dbName.plain.bak")
        val encryptedTmp = File(parent, "$dbName.enc.tmp")
        encryptedTmp.delete()

        val keyHex = passphrase.joinToString("") { b -> "%02x".format(b) }

        return try {
            dbPath.copyTo(plainBackup, overwrite = true)

            // Plain öffnen (leerer Key = unverschlüsselt). API sqlcipher-android 4.x:
            // openDatabase(path, password, cursorFactory, flags, databaseHook, errorHandler)
            val plain = SQLiteDatabase.openDatabase(
                dbPath.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )
            val userVersion: Int
            try {
                userVersion = plain.version
                val encPath = encryptedTmp.absolutePath.replace("'", "''")
                // Hex-Key laut Zetetic: KEY "x'HEX...'"
                plain.rawExecSQL("ATTACH DATABASE '$encPath' AS encrypted KEY \"x'$keyHex'\"")
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plain.close()
            }

            require(encryptedTmp.exists() && encryptedTmp.length() > 100L) {
                "SQLCipher-Export erzeugte keine gültige Datei"
            }

            // Room-user_version auf die neue Datei übernehmen
            val encDb = SQLiteDatabase.openDatabase(
                encryptedTmp.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )
            try {
                encDb.version = userVersion
            } finally {
                encDb.close()
            }

            // WAL/SHM der plain-DB entfernen, dann ersetzen
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(dbPath.path + suffix).delete()
            }
            if (!dbPath.delete()) {
                Log.w(TAG, "plain DB delete() fehlgeschlagen – overwrite")
            }
            if (!encryptedTmp.renameTo(dbPath)) {
                encryptedTmp.copyTo(dbPath, overwrite = true)
                encryptedTmp.delete()
            }

            Log.i(TAG, "Migration OK – Klartext-Backup: ${plainBackup.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Migration fehlgeschlagen – stelle Backup wieder her", e)
            runCatching {
                encryptedTmp.delete()
                if (plainBackup.exists()) {
                    plainBackup.copyTo(dbPath, overwrite = true)
                }
            }
            false
        }
    }

    fun isPlainSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        val header = ByteArray(16)
        FileInputStream(file).use { it.read(header) }
        return String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
    }

    /** SQLCipher: Datei existiert, aber kein Klartext-SQLite-Header. */
    fun isSqlCipherDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < 512L) return false
        return !isPlainSqlite(file)
    }

    /**
     * Harte Validierung einer SQLCipher-Datei: Die Datei wird mit dem
     * übergebenen Passphrase tatsächlich geöffnet und per Query geprüft.
     * Damit wird verhindert, dass beliebige (korrupte/fremde) Dateien als
     * „Backup" durch einen Restore die Live-DB überschreiben.
     *
     * @return true, wenn die Datei eine gültige, mit [passphrase] verschlüsselte
     *         SQLCipher-Datenbank ist.
     */
    fun validateSqlCipherFile(file: File, passphrase: ByteArray): Boolean {
        if (!file.exists() || file.length() < 512L) return false
        loadNativeMustHaveContext() // no-op, setzt nur den loaded-Flag
        return try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                db.rawQuery("SELECT count(*) FROM sqlite_master", emptyArray()).use { cursor ->
                    cursor.moveToFirst() && cursor.columnCount > 0
                }
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Hinweis: [loadNative] benötigt einen Context zum Laden der nativen Libs.
     * Für die Validierung setzen wir voraus, dass sie bereits geladen ist
     * (der Aufrufer – BackupManager – läuft im laufenden App-Kontext). Ist das
     * nicht der Fall, wird die Validierung mit Fehler abgelehnt.
     */
    private fun loadNativeMustHaveContext() {
        check(loaded) { "SQLCipher native libs nicht geladen – BackupManager läuft im App-Kontext" }
    }
}
