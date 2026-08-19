package com.secureguard.enterprise.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Platzhalter: SecureGuardRepository, SettingsRepository und ScanResultStore
 * werden per Konstruktor-@Inject bereitgestellt. Eine zweite @Provides-Bindung
 * würde Hilt mit "bound multiple times" abbrechen.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
