package com.secureguard.enterprise.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.repository.AppSettings
import com.secureguard.enterprise.data.repository.SettingsRepository
import com.secureguard.enterprise.presentation.ui.common.missingPermissions
import com.secureguard.enterprise.presentation.ui.common.requiredPermissions
import com.secureguard.enterprise.services.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PermissionRow(
    val name: String,
    val granted: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val uiState: StateFlow<AppSettings> = settingsRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsRepository.current)

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<List<PermissionRow>> = _permissions.asStateFlow()

    private val _pinMessage = MutableStateFlow<String?>(null)
    val pinMessage: StateFlow<String?> = _pinMessage.asStateFlow()

    val pinConfigured: Boolean get() = authManager.isPinConfigured()

    fun setNotifications(value: Boolean) = settingsRepository.update { it.copy(notificationsEnabled = value) }
    fun setExternalCrowd(value: Boolean) = settingsRepository.update { it.copy(externalCrowdAllowed = value) }
    fun setOfflineOnly(value: Boolean) = settingsRepository.update { it.copy(offlineOnly = value) }
    fun setLearning(value: Boolean) = settingsRepository.update { it.copy(learningMode = value) }
    fun setDarkMode(value: Boolean) = settingsRepository.update { it.copy(darkMode = value) }
    fun setConsent(value: Boolean) = settingsRepository.update { it.copy(consentGiven = value) }
    fun setUserName(value: String) = settingsRepository.update { it.copy(userName = value) }
    fun setOrganization(value: String) = settingsRepository.update { it.copy(organization = value) }
    fun setLoraEndpoint(value: String) = settingsRepository.update { it.copy(loraEndpoint = value) }
    fun setOpticalEndpoint(value: String) = settingsRepository.update { it.copy(opticalEndpoint = value) }
    fun setUrbanEndpoint(value: String) = settingsRepository.update { it.copy(urbanEndpoint = value) }
    fun setCrowdEndpoint(value: String) = settingsRepository.update { it.copy(crowdEndpoint = value) }

    fun refreshPermissions() {
        _permissions.value = readPermissions()
    }

    fun requiredPermissionArray(): Array<String> = requiredPermissions()

    fun missingCount(): Int = missingPermissions(context).size

    fun configurePin(pin: String) {
        _pinMessage.value = if (authManager.configurePin(pin)) {
            "PIN gespeichert – App ist beim nächsten Start gesperrt."
        } else {
            "PIN muss mindestens 4 Zeichen haben."
        }
    }

    fun disablePin() {
        authManager.disablePin()
        _pinMessage.update { "PIN entfernt." }
    }

    private fun readPermissions(): List<PermissionRow> {
        val names = mapOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION to "Standort",
            android.Manifest.permission.ACCESS_COARSE_LOCATION to "Grober Standort",
            android.Manifest.permission.CAMERA to "Kamera",
            android.Manifest.permission.POST_NOTIFICATIONS to "Benachrichtigungen",
            android.Manifest.permission.BLUETOOTH_SCAN to "Bluetooth-Scan",
            android.Manifest.permission.BLUETOOTH_CONNECT to "Bluetooth-Verbindung",
            android.Manifest.permission.NEARBY_WIFI_DEVICES to "WLAN-Geräte in der Nähe"
        )
        return requiredPermissions().map { perm ->
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            PermissionRow(names[perm] ?: perm.substringAfterLast('.'), granted)
        }
    }
}
