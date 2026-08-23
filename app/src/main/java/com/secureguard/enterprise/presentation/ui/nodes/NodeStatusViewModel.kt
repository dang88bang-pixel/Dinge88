package com.secureguard.enterprise.presentation.ui.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.agent.NodeStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NodeStatusViewModel @Inject constructor(
    private val apiNodeManager: ApiNodeManager,
    private val repository: SecureGuardRepository
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

    /** Hinweistext zum letzten Volltest (leer = noch keiner). */
    private val _lastQueryInfo = MutableStateFlow("")
    val lastQueryInfo: StateFlow<String> = _lastQueryInfo.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            apiNodeManager.refreshHealth()
            _lastRefresh.value = SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            ).format(Date())
            _isLoading.value = false
        }
    }

    /**
     * Führt eine komplette Abfrage über alle Knoten aus – gegen das erste
     * Whitelist-Asset (echte MAC, echte letzte Position). Ohne Asset wird
     * der Test abgelehnt und der Grund angezeigt.
     */
    fun runFullQuery() {
        viewModelScope.launch {
            _isLoading.value = true
            val assets = repository.getWhitelistedAssets().first()
            val target = assets.firstOrNull()
            if (target == null) {
                _lastQueryInfo.value = "Kein Asset vorhanden – Volltest nicht möglich"
            } else {
                apiNodeManager.queryAllNodes(
                    mac = target.mac,
                    latitude = target.latitude,
                    longitude = target.longitude
                )
                _lastQueryInfo.value =
                    "Volltest gegen ${target.shortName} (${target.mac})"
                _lastRefresh.value = SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())
            }
            _isLoading.value = false
        }
    }

    fun toggleNode(nodeId: String) {
        apiNodeManager.toggleNode(nodeId)
    }

    fun isNodeEnabled(nodeId: String): Boolean = apiNodeManager.isNodeEnabled(nodeId)
}
