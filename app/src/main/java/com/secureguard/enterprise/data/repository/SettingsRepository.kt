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

    companion object {
        private const val PREFS_NAME = "secureguard_settings"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_BLUETOOTH = "bluetooth"
        private const val KEY_WIFI = "wifi"
        private const val KEY_LOCATION = "location"
    }
}
