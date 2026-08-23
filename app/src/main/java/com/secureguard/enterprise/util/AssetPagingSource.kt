package com.secureguard.enterprise.util

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository

/**
 * Paging-Source für Lazy Loading der Asset-Liste (Paging 3).
 *
 * Filter (Suchtext + Status) werden über Provider-Funktionen gelesen, damit
 * die laufende Suche/der Tab-Wechsel ohne Neubau des Pagers wirkt.
 *
 * Nutzung:
 * ```
 * Pager(PagingConfig(pageSize = 20)) {
 *     AssetPagingSource(repo, filterProvider = { query }, statusProvider = { tab })
 * }.flow.cachedIn(viewModelScope)
 * ```
 */
class AssetPagingSource(
    private val repository: SecureGuardRepository,
    private val filterProvider: () -> String? = { null },
    private val statusProvider: () -> AssetStatus? = { null }
) : PagingSource<Int, Asset>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Asset> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            val assets = repository.getAssetsPaginated(
                offset = page * pageSize,
                limit = pageSize,
                filter = filterProvider()?.takeIf { it.isNotBlank() },
                status = statusProvider()
            )
            LoadResult.Page(
                data = assets,
                prevKey = if (page > 0) page - 1 else null,
                nextKey = if (assets.size == pageSize) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Asset>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
        }
}
