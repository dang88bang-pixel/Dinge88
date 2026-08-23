package com.secureguard.enterprise.services

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zur Laufzeit konfigurierbare Einstellungen (SharedPreferences
 * `secureguard_settings`), die von den Detektions-Kanälen und dem Agenten
 * gelesen werden.
 *
 * Zentrale Regeln:
 * - **[demoMode]** ist standardmäßig AUS. Nur wenn der Modus explizit
 *   aktiviert wurde, liefern die Kanäle simulierte Demo-Daten. Ohne Demo-Modus
 *   geben nicht konfigurierte / nicht erreichbare Quellen ehrlich `null`
 *   zurück.
 * - **Endpunkt-URLs** schalten die Remote-Kanäle (LoRaWAN-Backend,
 *   Optik-Inferenz, Urban-Infrastruktur, Crowd-Proxy) auf echte Quellen um.
 *   Leer = Kanal inaktiv (kein Fake).
 */
@Singleton
class RuntimeSettings @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    data class RuntimeSettingsState(
        val demoMode: Boolean = false,
        val loraEndpoint: String = "",
        val loraApiKey: String = "",
        val opticalEndpoint: String = "",
        val urbanEndpoint: String = "",
        val crowdEndpoint: String = ""
    )

    private val _state = MutableStateFlow(load())
    val state: StateFlow<RuntimeSettingsState> = _state.asStateFlow()

    val demoMode: Boolean get() = _state.value.demoMode
    val loraEndpoint: String get() = _state.value.loraEndpoint.trim()
    val loraApiKey: String get() = _state.value.loraApiKey.trim()
    val opticalEndpoint: String get() = _state.value.opticalEndpoint.trim()
    val urbanEndpoint: String get() = _state.value.urbanEndpoint.trim()
    val crowdEndpoint: String get() = _state.value.crowdEndpoint.trim()

    fun setDemoMode(enabled: Boolean) = update { it.copy(demoMode = enabled) }
    fun setLoraEndpoint(url: String) = update { it.copy(loraEndpoint = url) }
    fun setLoraApiKey(key: String) = update { it.copy(loraApiKey = key) }
    fun setOpticalEndpoint(url: String) = update { it.copy(opticalEndpoint = url) }
    fun setUrbanEndpoint(url: String) = update { it.copy(urbanEndpoint = url) }
    fun setCrowdEndpoint(url: String) = update { it.copy(crowdEndpoint = url) }

    private fun load(): RuntimeSettingsState = RuntimeSettingsState(
        demoMode = prefs.getBoolean(KEY_DEMO, false),
        loraEndpoint = prefs.getString(KEY_LORA_URL, "").orEmpty(),
        loraApiKey = prefs.getString(KEY_LORA_KEY, "").orEmpty(),
        opticalEndpoint = prefs.getString(KEY_OPTICAL_URL, "").orEmpty(),
        urbanEndpoint = prefs.getString(KEY_URBAN_URL, "").orEmpty(),
        crowdEndpoint = prefs.getString(KEY_CROWD_URL, "").orEmpty()
    )

    private fun update(transform: (RuntimeSettingsState) -> RuntimeSettingsState) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean(KEY_DEMO, next.demoMode)
            .putString(KEY_LORA_URL, next.loraEndpoint)
            .putString(KEY_LORA_KEY, next.loraApiKey)
            .putString(KEY_OPTICAL_URL, next.opticalEndpoint)
            .putString(KEY_URBAN_URL, next.urbanEndpoint)
            .putString(KEY_CROWD_URL, next.crowdEndpoint)
            .apply()
        _state.value = next
    }

    companion object {
        const val KEY_DEMO = "demo_mode"
        const val KEY_LORA_URL = "endpoint_lora"
        const val KEY_LORA_KEY = "lora_api_key"
        const val KEY_OPTICAL_URL = "endpoint_optical"
        const val KEY_URBAN_URL = "endpoint_urban"
        const val KEY_CROWD_URL = "endpoint_crowd"
    }
}
