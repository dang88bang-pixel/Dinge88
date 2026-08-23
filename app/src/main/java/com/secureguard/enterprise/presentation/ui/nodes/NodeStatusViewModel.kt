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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    /** Runs a full query across all nodes using the first asset from the database. */
    fun runFullQuery() {
        viewModelScope.launch {
            val assets = repository.getAllAssets().first()
            val testAsset = assets.firstOrNull() ?: return@launch
            apiNodeManager.queryAllNodes(
                mac = testAsset.mac,
                latitude = testAsset.latitude,
                longitude = testAsset.longitude
            )
            refresh()
        }
    }

    fun toggleNode(nodeId: String) {
        apiNodeManager.toggleNode(nodeId)
    }

    fun isNodeEnabled(nodeId: String): Boolean = apiNodeManager.isNodeEnabled(nodeId)
}
