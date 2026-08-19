package com.secureguard.enterprise.di

import com.secureguard.enterprise.data.database.SecureGuardDatabase
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSecureGuardRepository(
        database: SecureGuardDatabase
    ): SecureGuardRepository = SecureGuardRepository(database)
}
