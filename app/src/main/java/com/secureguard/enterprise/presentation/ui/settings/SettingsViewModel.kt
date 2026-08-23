package com.secureguard.enterprise.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.DemoDataManager
import com.secureguard.enterprise.services.RuntimeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val externalCrowdAllowed: Boolean = false,
    val offlineOnly: Boolean = true,
    val learningMode: Boolean = true,
    val darkMode: Boolean = false,
    val consentGiven: Boolean = true,
    // Profil (editierbar, lokal persistiert)
    val profileName: String = "Wache Mitte",
    val profileOrg: String = "SecureGuard Enterprise",
    // Demo-Modus & Backend-Endpunkte
    val demoMode: Boolean = false,
    val loraEndpoint: String = "",
    val loraApiKey: String = "",
    val opticalEndpoint: String = "",
    val urbanEndpoint: String = "",
    val crowdEndpoint: String = "",
    val demoDataLoaded: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val runtimeSettings: RuntimeSettings,
    private val demoDataManager: DemoDataManager
) : ViewModel() {

    private val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Demo-Daten-Status aktuell halten (auch nach App-Neustart).
        viewModelScope.launch {
            val loaded = runCatching { demoDataManager.isDemoDataLoaded() }.getOrDefault(false)
            _uiState.update { it.copy(demoDataLoaded = loaded) }
        }
    }

    private fun load() = SettingsUiState(
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        externalCrowdAllowed = prefs.getBoolean(KEY_CROWD, false),
        offlineOnly = prefs.getBoolean(KEY_OFFLINE, true),
        learningMode = prefs.getBoolean(KEY_LEARNING, true),
        darkMode = prefs.getBoolean(KEY_DARK, false),
        consentGiven = prefs.getBoolean(KEY_CONSENT, true),
        profileName = prefs.getString(KEY_PROFILE_NAME, "Wache Mitte").orEmpty(),
        profileOrg = prefs.getString(KEY_PROFILE_ORG, "SecureGuard Enterprise").orEmpty(),
        demoMode = prefs.getBoolean(RuntimeSettings.KEY_DEMO, false),
        loraEndpoint = prefs.getString(RuntimeSettings.KEY_LORA_URL, "").orEmpty(),
        loraApiKey = prefs.getString(RuntimeSettings.KEY_LORA_KEY, "").orEmpty(),
        opticalEndpoint = prefs.getString(RuntimeSettings.KEY_OPTICAL_URL, "").orEmpty(),
        urbanEndpoint = prefs.getString(RuntimeSettings.KEY_URBAN_URL, "").orEmpty(),
        crowdEndpoint = prefs.getString(RuntimeSettings.KEY_CROWD_URL, "").orEmpty()
    )

    private fun save(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { current ->
            val next = transform(current)
            prefs.edit()
                .putBoolean(KEY_NOTIFICATIONS, next.notificationsEnabled)
                .putBoolean(KEY_CROWD, next.externalCrowdAllowed)
                .putBoolean(KEY_OFFLINE, next.offlineOnly)
                .putBoolean(KEY_LEARNING, next.learningMode)
                .putBoolean(KEY_DARK, next.darkMode)
                .putBoolean(KEY_CONSENT, next.consentGiven)
                .putString(KEY_PROFILE_NAME, next.profileName)
                .putString(KEY_PROFILE_ORG, next.profileOrg)
                .apply()
            next
        }
    }

    fun setNotifications(value: Boolean) = save { it.copy(notificationsEnabled = value) }
    fun setExternalCrowd(value: Boolean) = save { it.copy(externalCrowdAllowed = value) }
    fun setOfflineOnly(value: Boolean) = save { it.copy(offlineOnly = value) }
    fun setLearning(value: Boolean) = save { it.copy(learningMode = value) }
    fun setDarkMode(value: Boolean) = save { it.copy(darkMode = value) }
    fun setConsent(value: Boolean) = save { it.copy(consentGiven = value) }

    fun setProfileName(value: String) = save { it.copy(profileName = value) }
    fun setProfileOrg(value: String) = save { it.copy(profileOrg = value) }

    // ============ DEMO-MODUS & BACKEND-ENDPUNKTE ============

    /**
     * Schaltet den Demo-Modus um. Beim **Aktivieren** werden optional Demo-
     * Assets geladen, beim **Deaktivieren** wieder entfernt – simulation bleibt
     * damit immer explizit und sichtbar.
     */
    fun setDemoMode(enabled: Boolean) {
        runtimeSettings.setDemoMode(enabled)
        _uiState.update { it.copy(demoMode = enabled) }
        viewModelScope.launch {
            if (enabled) {
                runCatching { demoDataManager.seed() }
            } else {
                runCatching { demoDataManager.clear() }
            }
            _uiState.update {
                it.copy(demoDataLoaded = runCatching { demoDataManager.isDemoDataLoaded() }
                    .getOrDefault(false))
            }
        }
    }

    fun setLoraEndpoint(url: String) {
        runtimeSettings.setLoraEndpoint(url)
        _uiState.update { it.copy(loraEndpoint = url) }
    }

    fun setLoraApiKey(key: String) {
        runtimeSettings.setLoraApiKey(key)
        _uiState.update { it.copy(loraApiKey = key) }
    }

    fun setOpticalEndpoint(url: String) {
        runtimeSettings.setOpticalEndpoint(url)
        _uiState.update { it.copy(opticalEndpoint = url) }
    }

    fun setUrbanEndpoint(url: String) {
        runtimeSettings.setUrbanEndpoint(url)
        _uiState.update { it.copy(urbanEndpoint = url) }
    }

    fun setCrowdEndpoint(url: String) {
        runtimeSettings.setCrowdEndpoint(url)
        _uiState.update { it.copy(crowdEndpoint = url) }
    }

    companion object {
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_CROWD = "external_crowd"
        private const val KEY_OFFLINE = "offline_only"
        private const val KEY_LEARNING = "learning_mode"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_CONSENT = "gdpr_consent"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_PROFILE_ORG = "profile_org"
    }
}
