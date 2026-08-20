package com.secureguard.enterprise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.secureguard.enterprise.data.local.dao.AlertDao
import com.secureguard.enterprise.data.local.dao.AssetDao
import com.secureguard.enterprise.data.local.dao.DetectionDao
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection

@Database(
    entities = [Asset::class, Detection::class, Alert::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SecureGuardDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun detectionDao(): DetectionDao
    abstract fun alertDao(): AlertDao

    companion object {
        const val DATABASE_NAME = "secureguard.db"
    }
}
