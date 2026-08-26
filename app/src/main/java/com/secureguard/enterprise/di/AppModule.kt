package com.secureguard.enterprise.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.local.SqlCipherHelper
import com.secureguard.enterprise.data.local.dao.AlertDao
import com.secureguard.enterprise.data.local.dao.AssetDao
import com.secureguard.enterprise.data.local.dao.AuditLogDao
import com.secureguard.enterprise.data.local.dao.DetectionDao
import com.secureguard.enterprise.data.local.dao.PendingActionDao
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.data.repository.SecureGuardRepositoryImpl
import com.secureguard.enterprise.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TAG = "AppModule"

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): SecureGuardDatabase {
        val passphrase = keyManager.getOrCreatePassphrase()

        // Einmalige Migration plain → SQLCipher (falls Alt-Installation)
        runCatching {
            SqlCipherHelper.migratePlainToEncryptedIfNeeded(
                context = context,
                dbName = SecureGuardDatabase.DATABASE_NAME,
                passphrase = passphrase.copyOf()
            )
        }.onFailure {
            Log.e(TAG, "SQLCipher-Migration fehlgeschlagen – versuche encrypted open", it)
        }

        val factory = SqlCipherHelper.createFactory(context, passphrase)

        return Room.databaseBuilder(
            context,
            SecureGuardDatabase::class.java,
            SecureGuardDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(SecureGuardDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
            .also {
                Log.i(TAG, "Room+SQLCipher geöffnet (${SecureGuardDatabase.DATABASE_NAME})")
            }
    }

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
