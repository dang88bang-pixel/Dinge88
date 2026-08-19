package com.secureguard.enterprise.presentation.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel für die Fernsteuerungs-Aktionen (Alarm, Motor, Batterie, ...).
 * Platzhalter – die eigentliche Ausführung wird über Dienste/Backend angebunden.
 */
@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _selectedAssetId = MutableStateFlow<String?>(null)
    val selectedAssetId: StateFlow<String?> = _selectedAssetId.asStateFlow()

    private val _lastAction = MutableStateFlow<String?>(null)
    val lastAction: StateFlow<String?> = _lastAction.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllAssets().collect { _assets.value = it }
        }
    }

    fun selectAsset(id: String) {
        _selectedAssetId.value = id
    }

    fun triggerAction(action: String) {
        _lastAction.value = action
        // TODO: Aktion an das Asset senden (z. B. über LoRa/BLE/Backend).
    }
}
