package com.secureguard.enterprise.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AgentSettings
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _agentStatus = MutableStateFlow(false)
    val agentStatus: StateFlow<Boolean> = _agentStatus.asStateFlow()

    init {
        loadAssets()
        monitorAgentStatus()
        startAgent()
    }

    private fun loadAssets() {
        viewModelScope.launch {
            repository.getWhitelistedAssets().collect { assetList ->
                _assets.value = assetList
                updateStats(assetList)
            }
        }
    }

    private fun monitorAgentStatus() {
        viewModelScope.launch {
            agentService.agentStatus.collect { status ->
                _agentStatus.value = status.running
                _uiState.update { state ->
                    state.copy(
                        agentRunning = status.running,
                        lastSyncTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    )
                }
            }
        }
    }

    private fun updateStats(assetList: List<Asset>) {
        val total = assetList.size
        val online = assetList.count { it.status == AssetStatus.ONLINE }
        val maintenance = assetList.count { it.status == AssetStatus.MAINTENANCE }
        val offline = assetList.count { it.status == AssetStatus.OFFLINE }
        val searching = assetList.count { it.status == AssetStatus.SEARCHING }

        _uiState.update { state ->
            state.copy(
                totalAssets = total,
                onlineAssets = online,
                offlineAssets = offline,
                maintenanceAssets = maintenance,
                activeSearches = searching,
                alertCount = maintenance + offline
            )
        }
    }

    private fun defaultSettings() = AgentSettings(
        interval = 30,
        dynamicPriority = true,
        learningMode = true,
        offlineOnly = true,
        externalSources = false
    )

    private fun startAgent() {
        agentService.start(defaultSettings())
    }

    fun refresh() {
        viewModelScope.launch {
            loadAssets()
        }
    }

    fun navigateToDetail(assetId: String) {
        // Wird in der UI behandelt
    }

    fun navigateToAddAsset() {
        // Wird in der UI behandelt
    }

    fun startQRScan() {
        // Wird in der UI behandelt
    }

    fun toggleAgent() {
        if (_agentStatus.value) {
            agentService.stop()
        } else {
            startAgent()
        }
    }

    override fun onCleared() {
        agentService.stop()
        super.onCleared()
    }
}

data class DashboardUiState(
    val totalAssets: Int = 0,
    val onlineAssets: Int = 0,
    val offlineAssets: Int = 0,
    val maintenanceAssets: Int = 0,
    val activeSearches: Int = 0,
    val alertCount: Int = 0,
    val agentRunning: Boolean = false,
    val batteryLevel: Int = 87,
    val lastSyncTime: String = "--:--"
)
