package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class AddAssetUiState(
    val name: String = "",
    val shortName: String = "",
    val mac: String = "",
    val vin: String = "",
    val externalAllowed: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddAssetViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val roleManager: RoleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAssetUiState())
    val uiState: StateFlow<AddAssetUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onShortNameChange(value: String) = _uiState.update { it.copy(shortName = value) }
    fun onMacChange(value: String) = _uiState.update { it.copy(mac = value.uppercase()) }
    fun onVinChange(value: String) = _uiState.update { it.copy(vin = value) }
    fun onExternalChange(value: Boolean) = _uiState.update { it.copy(externalAllowed = value) }

    fun save() {
        // RBAC (F-44): Assets anlegen/bearbeiten erfordert EDIT_ASSETS
        if (!roleManager.require(Permission.EDIT_ASSETS)) {
            _uiState.update { it.copy(error = "Keine Berechtigung (Rolle ${roleManager.currentRole})") }
            return
        }
        val state = _uiState.value
        if (state.name.isBlank() || state.mac.isBlank()) {
            _uiState.update { it.copy(error = "Name und MAC-Adresse sind erforderlich") }
            return
        }
        if (!MAC_REGEX.matches(state.mac)) {
            _uiState.update { it.copy(error = "MAC-Adresse ungültig (AA:BB:CC:DD:EE:FF)") }
            return
        }
        viewModelScope.launch {
            val asset = Asset(
                id = "asset-" + UUID.randomUUID().toString().take(8),
                name = state.name,
                shortName = state.shortName.ifBlank { state.name },
                mac = state.mac,
                vin = state.vin.ifBlank { null },
                status = AssetStatus.UNKNOWN,
                externalAllowed = state.externalAllowed,
                whitelisted = true,
                createdAt = Date(),
                updatedAt = Date()
            )
            repository.upsertAsset(asset)
            _uiState.update { it.copy(saved = true, error = null) }
        }
    }

    fun consumeNavigation() = _uiState.update { it.copy(saved = false) }

    companion object {
        private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }
}
