package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _selectedAsset = MutableStateFlow<Asset?>(null)
    val selectedAsset: StateFlow<Asset?> = _selectedAsset.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllAssets().collect { _assets.value = it }
        }
    }

    fun selectAsset(id: String) {
        viewModelScope.launch {
            val asset = _assets.value.firstOrNull { it.id == id }
            _selectedAsset.value = asset
            if (asset != null) {
                repository.getDetections(asset.mac).collect { _detections.value = it }
            }
        }
    }

    fun clearSelection() {
        _selectedAsset.value = null
        _detections.value = emptyList()
    }

    fun setStatus(id: String, status: AssetStatus) {
        viewModelScope.launch {
            val asset = _assets.value.firstOrNull { it.id == id } ?: return@launch
            val updated = asset.copy(status = status)
            repository.updateAsset(updated)
        }
    }

    fun removeAsset(asset: Asset) {
        viewModelScope.launch {
            repository.deleteAsset(asset)
        }
    }

    fun addSampleAsset() {
        viewModelScope.launch {
            val existing = _assets.value
            val sample = Asset(
                id = "asset-${System.currentTimeMillis()}",
                name = "Demo-Fahrzeug",
                mac = "AA:BB:CC:DD:EE:FF",
                shortName = "Demo",
                icon = "🚗",
                status = AssetStatus.OFFLINE,
                whitelisted = true
            )
            if (existing.none { it.id == sample.id }) {
                repository.insertAsset(sample)
            }
        }
    }
}
