package com.secureguard.enterprise.presentation.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllAssets().collect { assetList ->
                _assets.value = assetList
                updateStats(assetList)
            }
        }
        viewModelScope.launch {
            repository.getUnresolvedAlerts().collect { alerts ->
                _uiState.update { it.copy(alertCount = alerts.size) }
            }
        }
    }

    private fun updateStats(assetList: List<Asset>) {
        val total = assetList.size
        val online = assetList.count { it.status == AssetStatus.ONLINE }
        _uiState.update { state ->
            state.copy(
                totalAssets = total,
                onlineAssets = online,
                activeSearches = assetList.count { it.status == AssetStatus.SEARCHING }
            )
        }
    }

    fun refresh() {
        // Manuelle Aktualisierung – Daten fließen über Flows automatisch ein.
    }
}

data class DashboardUiState(
    val totalAssets: Int = 0,
    val onlineAssets: Int = 0,
    val activeSearches: Int = 0,
    val alertCount: Int = 0,
    val agentRunning: Boolean = true,
    val batteryLevel: Int = 87,
    val lastSyncTime: String = "10:25"
)
