package com.secureguard.enterprise.presentation.ui.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.agent.NodeStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.SatelliteService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodeStatusViewModel @Inject constructor(
    private val apiNodeManager: ApiNodeManager,
    private val repository: SecureGuardRepository,
    private val satelliteService: SatelliteService
) : ViewModel() {

    val nodeStatus: StateFlow<Map<String, NodeStatus>> =
        apiNodeManager.nodeStatus.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap()
        )

    val isQuerying: StateFlow<Boolean> = apiNodeManager.isQuerying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastRefresh = MutableStateFlow("–")
    val lastRefresh: StateFlow<String> = _lastRefresh.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            apiNodeManager.refreshHealth()
            _lastRefresh.value = java.text.SimpleDateFormat(
                "HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
            _isLoading.value = false
        }
    }

    /**
     * Führt eine komplette Abfrage über alle Knoten aus (Test-Suche) –
     * mit einem ECHTEN Asset aus der Whitelist und der echten GPS-Position
     * des Geräts als Mittelpunkt. Ohne Asset wird nicht abgefragt.
     */
    fun runFullQuery() {
        viewModelScope.launch {
            val assets = try {
                repository.getWhitelistedAssets().first()
            } catch (e: Exception) {
                emptyList()
            }
            val asset = assets.firstOrNull() ?: run {
                refresh()
                return@launch
            }
            val location = satelliteService.currentLocation()
            apiNodeManager.queryAllNodes(
                mac = asset.mac,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            refresh()
        }
    }

    fun toggleNode(nodeId: String) {
        apiNodeManager.toggleNode(nodeId)
    }

    fun isNodeEnabled(nodeId: String): Boolean = apiNodeManager.isNodeEnabled(nodeId)
}
