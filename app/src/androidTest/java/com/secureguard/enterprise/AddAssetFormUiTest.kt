package com.secureguard.enterprise

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.theme.SecureGuardTheme
import com.secureguard.enterprise.presentation.ui.assets.AddAssetScreen
import com.secureguard.enterprise.presentation.ui.assets.AddAssetViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI: Asset hinzufügen (Name/MAC/Speichern).
 * Keine Passwörter – nur Asset-Felder. PIN/Keystore legt der Anwender selbst fest.
 */
@RunWith(AndroidJUnit4::class)
class AddAssetFormUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var repo: FakeAssetRepository
    private lateinit var vm: AddAssetViewModel

    @Before
    fun setUp() {
        repo = FakeAssetRepository()
        vm = AddAssetViewModel(repo)
    }

    @Test
    fun form_fields_visible() {
        composeRule.setContent {
            SecureGuardTheme {
                val nav = rememberNavController()
                AddAssetScreen(navController = nav, viewModel = vm)
            }
        }
        composeRule.onNodeWithText("Asset hinzufügen", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("asset_name_field").assertIsDisplayed()
        composeRule.onNodeWithTag("asset_mac_field").assertIsDisplayed()
        composeRule.onNodeWithTag("asset_save_button").assertIsDisplayed()
    }

    @Test
    fun save_without_data_shows_error() {
        composeRule.setContent {
            SecureGuardTheme {
                val nav = rememberNavController()
                AddAssetScreen(navController = nav, viewModel = vm)
            }
        }
        composeRule.onNodeWithTag("asset_save_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("erforderlich", substring = true).assertIsDisplayed()
        assertTrue(repo.upserted.isEmpty())
    }

    @Test
    fun save_valid_asset_persists() {
        composeRule.setContent {
            SecureGuardTheme {
                val nav = rememberNavController()
                AddAssetScreen(navController = nav, viewModel = vm)
            }
        }
        composeRule.onNodeWithTag("asset_name_field").performTextInput("UI Test Asset")
        composeRule.onNodeWithTag("asset_mac_field").performTextInput("DE:AD:BE:EF:00:01")
        composeRule.onNodeWithTag("asset_save_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.upserted.isNotEmpty() }
        assertEquals(1, repo.upserted.size)
        assertEquals("UI Test Asset", repo.upserted[0].name)
        assertEquals("DE:AD:BE:EF:00:01", repo.upserted[0].mac)
        assertTrue(repo.upserted[0].whitelisted)
    }

    /** Minimaler Fake – nur upsert wird von AddAssetViewModel genutzt. */
    private class FakeAssetRepository : SecureGuardRepository {
        val upserted = mutableListOf<Asset>()
        override fun getWhitelistedAssets(): Flow<List<Asset>> = flowOf(emptyList())
        override fun getAllAssets(): Flow<List<Asset>> = flowOf(emptyList())
        override suspend fun getAssetByMac(mac: String): Asset? = null
        override suspend fun getAssetById(id: String): Asset? = null
        override suspend fun resolveAsset(idOrMac: String): Asset? = null
        override suspend fun upsertAsset(asset: Asset) {
            upserted += asset
        }
        override suspend fun updateAssetStatus(
            mac: String,
            status: AssetStatus,
            timestamp: Long,
            rssi: Int?,
            lat: Double?,
            lon: Double?
        ) = Unit
        override suspend fun deleteAsset(id: String) = Unit
        override suspend fun snapshotWhitelisted(): List<Asset> = emptyList()
        override fun getDetections(mac: String): Flow<List<Detection>> = flowOf(emptyList())
        override fun getAllDetections(): Flow<List<Detection>> = flowOf(emptyList())
        override suspend fun getLatestDetection(mac: String): Detection? = null
        override suspend fun insertDetection(detection: Detection): Long = 0L
        override suspend fun getAssetsPaginated(offset: Int, limit: Int, filter: String?): List<Asset> =
            emptyList()
        override suspend fun deleteDetectionsOlderThan(cutoff: Long): Int = 0
        override suspend fun deleteAlertsOlderThan(cutoff: Long): Int = 0
        override fun getAlerts(): Flow<List<Alert>> = flowOf(emptyList())
        override fun getUnacknowledgedAlerts(): Flow<List<Alert>> = flowOf(emptyList())
        override fun getUnacknowledgedAlertCount(): Flow<Int> = flowOf(0)
        override suspend fun insertAlert(alert: Alert): Long = 0L
        override suspend fun acknowledgeAlert(id: Long) = Unit
        override suspend fun acknowledgeAllAlerts() = Unit
        override suspend fun raiseAlert(
            assetId: String,
            type: AlertType,
            severity: AlertSeverity,
            message: String
        ): Long = 0L

    }
}
