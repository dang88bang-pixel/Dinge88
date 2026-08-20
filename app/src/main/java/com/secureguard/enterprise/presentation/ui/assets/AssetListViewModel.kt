package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AssetListUiState(
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    val maintenance: Int = 0
)

@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedStatus = MutableStateFlow<AssetStatus?>(null)

    val filteredAssets: StateFlow<List<Asset>> = combine(
        repository.getWhitelistedAssets(),
        searchQuery,
        selectedStatus
    ) { assets, query, status ->
        val q = query.trim().lowercase()
        assets.filter { asset ->
            val matchesSearch = q.isEmpty() ||
                asset.name.lowercase().contains(q) ||
                asset.shortName.lowercase().contains(q) ||
                asset.id.lowercase().contains(q) ||
                asset.mac.lowercase().contains(q)
            val matchesStatus = status == null || asset.status == status
            matchesSearch && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<AssetListUiState> = filteredAssets
        .map { list ->
            AssetListUiState(
                total = list.size,
                online = list.count { it.status == AssetStatus.ONLINE },
                offline = list.count { it.status == AssetStatus.OFFLINE },
                maintenance = list.count { it.status == AssetStatus.MAINTENANCE }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetListUiState())

    val currentQuery: StateFlow<String> = searchQuery
    val currentStatus: StateFlow<AssetStatus?> = selectedStatus

    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setStatusFilter(status: AssetStatus?) { selectedStatus.value = status }

    fun clearFilters() {
        searchQuery.value = ""
        selectedStatus.value = null
    }
}
