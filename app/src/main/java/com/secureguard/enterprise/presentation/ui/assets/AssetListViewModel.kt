package com.secureguard.enterprise.presentation.ui.assets

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
class AssetListViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _filteredAssets = MutableStateFlow<List<Asset>>(emptyList())
    val filteredAssets: StateFlow<List<Asset>> = _filteredAssets.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedStatus = MutableStateFlow<AssetStatus?>(null)
    val selectedStatus: StateFlow<AssetStatus?> = _selectedStatus.asStateFlow()

    private val _uiState = MutableStateFlow(AssetListUiState())
    val uiState: StateFlow<AssetListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getWhitelistedAssets().collect { assetList ->
                _assets.value = assetList
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        val query = _searchQuery.value.lowercase()
        val status = _selectedStatus.value

        val filtered = _assets.value.filter { asset ->
            val matchesSearch = query.isEmpty() ||
                asset.name.lowercase().contains(query) ||
                asset.shortName.lowercase().contains(query) ||
                asset.id.lowercase().contains(query) ||
                asset.mac.lowercase().contains(query)

            val matchesStatus = status == null || asset.status == status

            matchesSearch && matchesStatus
        }

        _filteredAssets.value = filtered
        updateStats(filtered)
    }

    private fun updateStats(filtered: List<Asset>) {
        _uiState.update { state ->
            state.copy(
                total = filtered.size,
                online = filtered.count { it.status == AssetStatus.ONLINE },
                offline = filtered.count { it.status == AssetStatus.OFFLINE },
                maintenance = filtered.count { it.status == AssetStatus.MAINTENANCE }
            )
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setStatusFilter(status: AssetStatus?) {
        _selectedStatus.value = status
        applyFilters()
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedStatus.value = null
        applyFilters()
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
}

data class AssetListUiState(
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    val maintenance: Int = 0
)
