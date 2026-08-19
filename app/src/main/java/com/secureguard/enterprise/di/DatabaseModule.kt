package com.secureguard.enterprise.di

import android.content.Context
import androidx.room.Room
import com.secureguard.enterprise.data.database.AlertDao
import com.secureguard.enterprise.data.database.AssetDao
import com.secureguard.enterprise.data.database.DetectionDao
import com.secureguard.enterprise.data.database.SecureGuardDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecureGuardDatabase {
        return Room.databaseBuilder(
            context,
            SecureGuardDatabase::class.java,
            "secureguard.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideAssetDao(database: SecureGuardDatabase): AssetDao = database.assetDao()

    @Provides
    fun provideDetectionDao(database: SecureGuardDatabase): DetectionDao = database.detectionDao()

    @Provides
    fun provideAlertDao(database: SecureGuardDatabase): AlertDao = database.alertDao()
}
