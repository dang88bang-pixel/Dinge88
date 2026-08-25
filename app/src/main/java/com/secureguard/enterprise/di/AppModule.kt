package com.secureguard.enterprise.di

import android.content.Context
import androidx.room.Room
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.local.dao.AlertDao
import com.secureguard.enterprise.data.local.dao.AssetDao
import com.secureguard.enterprise.data.local.dao.AuditLogDao
import com.secureguard.enterprise.data.local.dao.DetectionDao
import com.secureguard.enterprise.data.local.dao.PendingActionDao
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.data.repository.SecureGuardRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecureGuardDatabase =
        Room.databaseBuilder(
            context,
            SecureGuardDatabase::class.java,
            SecureGuardDatabase.DATABASE_NAME
        )
            .addMigrations(SecureGuardDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideAssetDao(db: SecureGuardDatabase): AssetDao = db.assetDao()

    @Provides
    fun provideDetectionDao(db: SecureGuardDatabase): DetectionDao = db.detectionDao()

    @Provides
    fun provideAlertDao(db: SecureGuardDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideAuditLogDao(db: SecureGuardDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    fun providePendingActionDao(db: SecureGuardDatabase): PendingActionDao = db.pendingActionDao()

    @Provides
    @Singleton
    fun provideRepository(
        assetDao: AssetDao,
        detectionDao: DetectionDao,
        alertDao: AlertDao
    ): SecureGuardRepository = SecureGuardRepositoryImpl(assetDao, detectionDao, alertDao)
}
