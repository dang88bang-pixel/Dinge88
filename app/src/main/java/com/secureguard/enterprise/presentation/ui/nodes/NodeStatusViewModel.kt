package com.secureguard.enterprise.presentation.ui.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.agent.NodeStatus
import com.secureguard.enterprise.data.local.SecureGuardDatabase
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
    private val database: SecureGuardDatabase
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

    /** info-Text zur letzten Test-Suche (z. B. verwendetes Asset / Fehler). */
    private val _lastQueryInfo = MutableStateFlow("")
    val lastQueryInfo: StateFlow<String> = _lastQueryInfo.asStateFlow()

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
     * Führt eine komplette Abfrage über alle Knoten aus (Test-Suche).
     * Verwendet das **erste echte Asset** aus der Datenbank (MAC + letzte
     * Position) statt einer festen Demo-Adresse. Ohne Assets gibt es einen
     * Hinweis statt einer Fake-Abfrage.
     */
    fun runFullQuery() {
        viewModelScope.launch {
            val asset = database.assetDao().observeAll().first().firstOrNull()
            if (asset == null) {
                _lastQueryInfo.value = "Kein Asset vorhanden – Test-Suche nicht möglich."
                return@launch
            }
            _lastQueryInfo.value = "Test-Suche für ${asset.shortName} (${asset.mac})"
            apiNodeManager.queryAllNodes(
                mac = asset.mac,
                latitude = asset.latitude ?: 52.52,
                longitude = asset.longitude ?: 13.40
            )
            refresh()
        }
    }

    fun toggleNode(nodeId: String) {
        apiNodeManager.toggleNode(nodeId)
    }

    fun isNodeEnabled(nodeId: String): Boolean = apiNodeManager.isNodeEnabled(nodeId)
}
