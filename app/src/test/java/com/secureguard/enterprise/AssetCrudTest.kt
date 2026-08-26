package com.secureguard.enterprise

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Asset CRUD über Repository + Room (in-memory).
 * Entspricht dem Checklisten-Punkt Asset-CRUD (ohne UI/Gerät).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssetCrudTest {

    private lateinit var db: SecureGuardDatabase
    private lateinit var repo: SecureGuardRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SecureGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SecureGuardRepositoryImpl(db.assetDao(), db.detectionDao(), db.alertDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sample(
        id: String = "asset-test-1",
        name: String = "Test Roller",
        mac: String = "AA:BB:CC:DD:EE:99"
    ) = Asset(
        id = id,
        name = name,
        shortName = name,
        mac = mac,
        status = AssetStatus.UNKNOWN,
        whitelisted = true,
        createdAt = Date(),
        updatedAt = Date()
    )

    @Test
    fun create_and_read_by_id_and_mac() = runBlocking {
        val asset = sample()
        repo.upsertAsset(asset)

        assertThat(repo.getAssetById(asset.id)).isNotNull()
        assertThat(repo.getAssetByMac(asset.mac)!!.name).isEqualTo("Test Roller")
        assertThat(repo.resolveAsset(asset.mac)!!.id).isEqualTo(asset.id)
        assertThat(repo.snapshotWhitelisted()).hasSize(1)
    }

    @Test
    fun update_status_and_list() = runBlocking {
        repo.upsertAsset(sample())
        repo.updateAssetStatus(
            mac = "AA:BB:CC:DD:EE:99",
            status = AssetStatus.ONLINE,
            timestamp = System.currentTimeMillis(),
            rssi = -42,
            lat = 52.52,
            lon = 13.40
        )
        val updated = repo.getAssetByMac("AA:BB:CC:DD:EE:99")!!
        assertThat(updated.status).isEqualTo(AssetStatus.ONLINE)
        assertThat(updated.rssi).isEqualTo(-42)
        assertThat(updated.latitude).isWithin(0.001).of(52.52)

        val list = repo.getWhitelistedAssets().first()
        assertThat(list).hasSize(1)
        assertThat(list[0].status).isEqualTo(AssetStatus.ONLINE)
    }

    @Test
    fun delete_removes_asset() = runBlocking {
        val a = sample()
        repo.upsertAsset(a)
        repo.deleteAsset(a.id)
        assertThat(repo.getAssetById(a.id)).isNull()
        assertThat(repo.snapshotWhitelisted()).isEmpty()
    }

    @Test
    fun upsert_second_asset_and_paginate() = runBlocking {
        repo.upsertAsset(sample(id = "a1", name = "Alpha", mac = "11:22:33:44:55:66"))
        repo.upsertAsset(sample(id = "a2", name = "Beta", mac = "66:55:44:33:22:11"))
        val page = repo.getAssetsPaginated(0, 10, null)
        assertThat(page).hasSize(2)
        val filtered = repo.getAssetsPaginated(0, 10, "Beta")
        assertThat(filtered).hasSize(1)
        assertThat(filtered[0].name).isEqualTo("Beta")
    }

    @Test
    fun invalid_mac_format_is_rejected_by_ui_regex_contract() {
        // Spiegel des AddAssetViewModel-Contracts (kein Persist ohne gültige MAC)
        val macRegex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        assertThat(macRegex.matches("AA:BB:CC:DD:EE:FF")).isTrue()
        assertThat(macRegex.matches("not-a-mac")).isFalse()
        assertThat(macRegex.matches("AABBCCDDEEFF")).isFalse()
    }
}
