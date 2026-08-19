package com.secureguard.enterprise.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistiert App-Einstellungen lokal in SharedPreferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Verhalten ---
    private val _notifications = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS, true)
    )
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _vibration = MutableStateFlow(
        prefs.getBoolean(KEY_VIBRATION, true)
    )
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _bluetooth = MutableStateFlow(
        prefs.getBoolean(KEY_BLUETOOTH, true)
    )
    val bluetooth: StateFlow<Boolean> = _bluetooth.asStateFlow()

    private val _wifi = MutableStateFlow(
        prefs.getBoolean(KEY_WIFI, false)
    )
    val wifi: StateFlow<Boolean> = _wifi.asStateFlow()

    private val _location = MutableStateFlow(
        prefs.getBoolean(KEY_LOCATION, true)
    )
    val location: StateFlow<Boolean> = _location.asStateFlow()

    // --- Backend-Endpunkte (Pilot-Projekt) ---
    private val _loRaUrl = MutableStateFlow(prefs.getString(KEY_LORA_URL, "").orEmpty())
    val loRaUrl: StateFlow<String> = _loRaUrl.asStateFlow()

    private val _opticalUrl = MutableStateFlow(prefs.getString(KEY_OPTICAL_URL, "").orEmpty())
    val opticalUrl: StateFlow<String> = _opticalUrl.asStateFlow()

    private val _urbanUrl = MutableStateFlow(prefs.getString(KEY_URBAN_URL, "").orEmpty())
    val urbanUrl: StateFlow<String> = _urbanUrl.asStateFlow()

    private val _crowdUrl = MutableStateFlow(prefs.getString(KEY_CROWD_URL, "").orEmpty())
    val crowdUrl: StateFlow<String> = _crowdUrl.asStateFlow()

    // --- Agent-Laufzeit ---
    private val _agentStartTime = MutableStateFlow(prefs.getLong(KEY_AGENT_START_TIME, 0L))
    val agentStartTime: StateFlow<Long> = _agentStartTime.asStateFlow()

    private val _agentDurationSeconds = MutableStateFlow(prefs.getLong(KEY_AGENT_DURATION_SECONDS, 0L))
    val agentDurationSeconds: StateFlow<Long> = _agentDurationSeconds.asStateFlow()

    fun setNotifications(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
        _notifications.value = value
    }

    fun setVibration(value: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, value).apply()
        _vibration.value = value
    }

    fun setBluetooth(value: Boolean) {
        prefs.edit().putBoolean(KEY_BLUETOOTH, value).apply()
        _bluetooth.value = value
    }

    fun setWifi(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI, value).apply()
        _wifi.value = value
    }

    fun setLocation(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION, value).apply()
        _location.value = value
    }

    fun setLoRaUrl(value: String) {
        prefs.edit().putString(KEY_LORA_URL, value).apply()
        _loRaUrl.value = value
    }

    fun setOpticalUrl(value: String) {
        prefs.edit().putString(KEY_OPTICAL_URL, value).apply()
        _opticalUrl.value = value
    }

    fun setUrbanUrl(value: String) {
        prefs.edit().putString(KEY_URBAN_URL, value).apply()
        _urbanUrl.value = value
    }

    fun setCrowdUrl(value: String) {
        prefs.edit().putString(KEY_CROWD_URL, value).apply()
        _crowdUrl.value = value
    }

    fun setAgentStartTime(value: Long) {
        prefs.edit().putLong(KEY_AGENT_START_TIME, value).apply()
        _agentStartTime.value = value
    }

    fun setAgentDurationSeconds(value: Long) {
        prefs.edit().putLong(KEY_AGENT_DURATION_SECONDS, value).apply()
        _agentDurationSeconds.value = value
    }

    companion object {
        private const val PREFS_NAME = "secureguard_settings"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_BLUETOOTH = "bluetooth"
        private const val KEY_WIFI = "wifi"
        private const val KEY_LOCATION = "location"
        private const val KEY_LORA_URL = "lora_url"
        private const val KEY_OPTICAL_URL = "optical_url"
        private const val KEY_URBAN_URL = "urban_url"
        private const val KEY_CROWD_URL = "crowd_url"
        private const val KEY_AGENT_START_TIME = "agent_start_time"
        private const val KEY_AGENT_DURATION_SECONDS = "agent_duration_seconds"
    }
}
