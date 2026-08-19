package com.secureguard.enterprise.presentation.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastUpdate = MutableStateFlow("--:--")
    val lastUpdate: StateFlow<String> = _lastUpdate.asStateFlow()

    init {
        loadAssets()
    }

    private fun loadAssets() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getWhitelistedAssets().collect { assetList ->
                _assets.value = assetList
                _lastUpdate.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadAssets()
    }

    fun zoomIn() {
        // Wird in der UI behandelt
    }

    fun zoomOut() {
        // Wird in der UI behandelt
    }

    fun centerOnAssets() {
        // Wird in der UI behandelt
    }
}
