package com.secureguard.enterprise

import com.google.common.truth.Truth.assertThat
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.assets.AddAssetViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Asset-Formular-Validierung (Name/MAC) – PIN/Passwörter sind hier nicht betroffen.
 * MAC- und Pflichtfeld-Regeln wie in der UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddAssetViewModelLogicTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: SecureGuardRepository
    private lateinit var vm: AddAssetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        coEvery { repo.upsertAsset(any()) } returns Unit
        vm = AddAssetViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun save_requires_name_and_mac() = runTest {
        vm.save()
        assertThat(vm.uiState.value.error).contains("erforderlich")
        coVerify(exactly = 0) { repo.upsertAsset(any()) }
    }

    @Test
    fun save_rejects_invalid_mac() = runTest {
        vm.onNameChange("Roller")
        vm.onMacChange("INVALID")
        vm.save()
        assertThat(vm.uiState.value.error).contains("MAC")
        coVerify(exactly = 0) { repo.upsertAsset(any()) }
    }

    @Test
    fun save_persists_valid_asset() = runTest {
        vm.onNameChange("E-Scooter Test")
        vm.onShortNameChange("Scooter T")
        vm.onMacChange("aa:bb:cc:dd:ee:10")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.saved).isTrue()
        coVerify(exactly = 1) {
            repo.upsertAsset(match<Asset> {
                it.name == "E-Scooter Test" &&
                    it.mac == "AA:BB:CC:DD:EE:10" &&
                    it.whitelisted
            })
        }
    }
}
