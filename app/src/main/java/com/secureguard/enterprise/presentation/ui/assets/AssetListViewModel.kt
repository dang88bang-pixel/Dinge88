package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import com.secureguard.enterprise.services.AgentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetListUiState(
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    val maintenance: Int = 0,
    val averageBattery: Int? = null,
    val weakSignal: Int = 0
)

/** Sortierkriterien der Asset-Liste. */
enum class AssetSort(val label: String) {
    NAME("Name"),
    STATUS("Status"),
    SIGNAL("Signal"),
    BATTERY("Akku"),
    LAST_SEEN("Zuletzt gesehen")
}

@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val roleManager: RoleManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedStatus = MutableStateFlow<AssetStatus?>(null)
    private val sortOrder = MutableStateFlow(AssetSort.STATUS)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val filteredAssets: StateFlow<List<Asset>> = combine(
        repository.getWhitelistedAssets(),
        searchQuery,
        selectedStatus,
        sortOrder
    ) { assets, query, status, sort ->
        val q = query.trim().lowercase()
        assets
            .filter { asset ->
                val matchesSearch = q.isEmpty() ||
                    asset.name.lowercase().contains(q) ||
                    asset.shortName.lowercase().contains(q) ||
                    asset.id.lowercase().contains(q) ||
                    asset.mac.lowercase().contains(q)
                val matchesStatus = status == null || asset.status == status
                matchesSearch && matchesStatus
            }
            .sortedWith(comparatorFor(sort))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<AssetListUiState> = filteredAssets
        .map { list ->
            val batteries = list.mapNotNull { it.batteryLevel }
            AssetListUiState(
                total = list.size,
                online = list.count { it.status == AssetStatus.ONLINE },
                offline = list.count { it.status == AssetStatus.OFFLINE },
                maintenance = list.count { it.status == AssetStatus.MAINTENANCE },
                averageBattery = if (batteries.isEmpty()) null else batteries.average().toInt(),
                weakSignal = list.count { it.rssi != 0 && it.rssi < -80 }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetListUiState())

    val currentQuery: StateFlow<String> = searchQuery
    val currentStatus: StateFlow<AssetStatus?> = selectedStatus
    val currentSort: StateFlow<AssetSort> = sortOrder

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setStatusFilter(status: AssetStatus?) { selectedStatus.value = status }
    fun setSort(sort: AssetSort) { sortOrder.value = sort }

    fun clearFilters() {
        searchQuery.value = ""
        selectedStatus.value = null
    }

    fun consumeMessage() { _message.value = null }

    /** Direktaktion aus der Liste heraus (Orten / Alarm) – inkl. RBAC-Prüfung. */
    fun quickAction(asset: Asset, actionType: ActionType) {
        viewModelScope.launch {
            if (!roleManager.require(Permission.EXECUTE_ACTIONS)) {
                _message.value = "Rolle ${roleManager.currentRole} darf keine Aktionen senden"
                return@launch
            }
            val delivered = runCatching { agentService.sendAction(asset, actionType.wireCommand) }
                .getOrDefault(false)
            _message.value = if (delivered) {
                "${actionType.label} an ${asset.shortName} zugestellt"
            } else {
                "${actionType.label} für ${asset.shortName} in die Warteschlange gelegt"
            }
        }
    }

    private fun comparatorFor(sort: AssetSort): Comparator<Asset> = when (sort) {
        AssetSort.NAME -> compareBy { it.shortName.lowercase() }
        AssetSort.STATUS -> compareBy<Asset> { statusRank(it.status) }.thenBy { it.shortName.lowercase() }
        AssetSort.SIGNAL -> compareByDescending { if (it.rssi == 0) Int.MIN_VALUE else it.rssi }
        AssetSort.BATTERY -> compareBy { it.batteryLevel ?: Int.MAX_VALUE }
        AssetSort.LAST_SEEN -> compareByDescending { it.lastSeen?.time ?: 0L }
    }

    private fun statusRank(status: AssetStatus): Int = when (status) {
        AssetStatus.OFFLINE -> 0
        AssetStatus.MAINTENANCE -> 1
        AssetStatus.SEARCHING -> 2
        AssetStatus.ONLINE -> 3
        AssetStatus.UNKNOWN -> 4
    }
}
