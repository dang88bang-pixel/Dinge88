package com.secureguard.enterprise.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
        @Volatile
        private var INSTANCE: SecureGuardDatabase? = null

        fun getInstance(context: Context): SecureGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureGuardDatabase::class.java,
                    "secureguard.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
