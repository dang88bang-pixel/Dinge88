package com.secureguard.enterprise.presentation.ui.addasset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.ScanResultStore
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAssetViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val scanResultStore: ScanResultStore
) : ViewModel() {

    private var saved = false

    /**
     * Das zuletzt gescannte QR-Ergebnis als reaktiver State. Wird beobachtet,
     * damit das Formular die MAC/ID automatisch übernimmt, sobald der Nutzer
     * vom Scan-Screen zurückkehrt.
     */
    val scannedValue: StateFlow<String?> = scanResultStore.lastScannedValue

    /** Leert den Scan-Store, damit ein späterer Scan nicht wiederverwendet wird. */
    fun consumeScannedValue() {
        scanResultStore.clear()
    }

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
