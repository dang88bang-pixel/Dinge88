package com.secureguard.enterprise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.secureguard.enterprise.data.local.dao.AlertDao
import com.secureguard.enterprise.data.local.dao.AssetDao
import com.secureguard.enterprise.data.local.dao.AuditLogDao
import com.secureguard.enterprise.data.local.dao.DetectionDao
import com.secureguard.enterprise.data.local.dao.PendingActionDao
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.PendingAction

@Database(
    entities = [
        Asset::class,
        Detection::class,
        Alert::class,
        AuditLog::class,
        PendingAction::class
    ],
    version = 2,
    // Schemas werden nach app/schemas exportiert (room.schemaLocation in build.gradle.kts)
    // → Voraussetzung für automatische Migration-Tests (room-testing).
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SecureGuardDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun detectionDao(): DetectionDao
    abstract fun alertDao(): AlertDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        const val DATABASE_NAME = "secureguard.db"

        /**
         * v1 → v2: Audit-Log- und Offline-Queue-Tabellen ergänzen.
         * Bestehende Daten (assets/detections/alerts) bleiben unverändert.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audit_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `deviceId` TEXT,
                        `ipAddress` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_actions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `assetMac` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `lastError` TEXT
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
