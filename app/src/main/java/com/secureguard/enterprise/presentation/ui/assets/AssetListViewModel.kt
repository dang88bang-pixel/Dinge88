package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.util.AssetPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AssetListUiState(
    val total: Int = 0,
    val online: Int = 0,
    val offline: Int = 0,
    val maintenance: Int = 0
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedStatus = MutableStateFlow<AssetStatus?>(null)

    /**
     * Echte Datenbank-Paginierung (Paging 3): Seite für Seite aus Room
     * geladen. Bei Änderung von Suchtext oder Status-Tab entsteht ein neuer
     * Pager (flatMapLatest) – die Liste lädt dann mit neuem Filter neu.
     */
    val pagedAssets: Flow<PagingData<Asset>> = combine(
        searchQuery,
        selectedStatus
    ) { query, status -> query to status }
        .flatMapLatest { (query, status) ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                    initialLoadSize = 40
                )
            ) {
                AssetPagingSource(
                    repository = repository,
                    filterProvider = { query },
                    statusProvider = { status }
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    /** Zähler aus der ungefilterten Whitelist (immer konsistent zur DB). */
    val uiState: StateFlow<AssetListUiState> = repository.getWhitelistedAssets()
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
