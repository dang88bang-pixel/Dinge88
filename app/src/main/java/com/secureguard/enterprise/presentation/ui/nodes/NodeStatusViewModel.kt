package com.secureguard.enterprise.presentation.ui.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.agent.NodeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodeStatusViewModel @Inject constructor(
    private val apiNodeManager: ApiNodeManager
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

    /** Führt eine komplette Abfrage über alle Knoten aus (Test-Suche). */
    fun runFullQuery() {
        viewModelScope.launch {
            apiNodeManager.queryAllNodes(
                mac = "AA:BB:CC:DD:EE:01",
                latitude = 52.52,
                longitude = 13.40
            )
            refresh()
        }
    }

    fun toggleNode(nodeId: String) {
        apiNodeManager.toggleNode(nodeId)
    }

    fun isNodeEnabled(nodeId: String): Boolean = apiNodeManager.isNodeEnabled(nodeId)
}
