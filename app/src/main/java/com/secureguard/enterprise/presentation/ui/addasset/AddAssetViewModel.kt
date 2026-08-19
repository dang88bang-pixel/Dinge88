package com.secureguard.enterprise.presentation.ui.addasset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAssetViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private var saved = false

    fun saveAsset(
        name: String,
        shortName: String,
        mac: String,
        vin: String,
        onDone: (Asset) -> Unit
    ) {
        viewModelScope.launch {
            val trimmedMac = mac.trim().uppercase()
            val id = if (trimmedMac.isNotEmpty()) "asset-$trimmedMac" else "asset-${System.currentTimeMillis()}"
            val asset = Asset(
                id = id,
                name = name.ifBlank { "Unbenannt" },
                mac = trimmedMac,
                vin = vin.trim().ifBlank { null },
                shortName = shortName.ifBlank { name.ifBlank { "Asset" } },
                icon = "🚗",
                status = AssetStatus.UNKNOWN,
                whitelisted = true
            )
            repository.insertAsset(asset)
            saved = true
            onDone(asset)
        }
    }

    fun wasSaved(): Boolean = saved
}
