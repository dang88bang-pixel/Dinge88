package com.secureguard.enterprise.data.repository

import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistierte App-Einstellungen (SharedPreferences).
 * Single Source of Truth für Theme, Agent, Profil und optionale Backend-URLs.
 */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val externalCrowdAllowed: Boolean = false,
    val offlineOnly: Boolean = true,
    val learningMode: Boolean = true,
    val darkMode: Boolean = false,
    val consentGiven: Boolean = true,
    val userName: String = "Wache Mitte",
    val organization: String = "SecureGuard Enterprise",
    val agentIntervalSec: Int = 30,
    val agentDuration: String = "unlimited",
    val agentCustomDays: Int = 0,
    val dynamicPriority: Boolean = true,
    val loraEndpoint: String = "",
    val opticalEndpoint: String = "",
    val urbanEndpoint: String = "",
    val crowdEndpoint: String = ""
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val systemDark = (
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ) == Configuration.UI_MODE_NIGHT_YES

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun load() = AppSettings(
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        externalCrowdAllowed = prefs.getBoolean(KEY_CROWD, false),
        offlineOnly = prefs.getBoolean(KEY_OFFLINE, true),
        learningMode = prefs.getBoolean(KEY_LEARNING, true),
        darkMode = prefs.getBoolean(KEY_DARK, systemDark),
        consentGiven = prefs.getBoolean(KEY_CONSENT, true),
        userName = prefs.getString(KEY_USER, "Wache Mitte") ?: "Wache Mitte",
        organization = prefs.getString(KEY_ORG, "SecureGuard Enterprise") ?: "SecureGuard Enterprise",
        agentIntervalSec = prefs.getInt(KEY_INTERVAL, 30),
        agentDuration = prefs.getString(KEY_DURATION, "unlimited") ?: "unlimited",
        agentCustomDays = prefs.getInt(KEY_CUSTOM_DAYS, 0),
        dynamicPriority = prefs.getBoolean(KEY_DYNAMIC, true),
        loraEndpoint = prefs.getString(KEY_LORA, "") ?: "",
        opticalEndpoint = prefs.getString(KEY_OPTICAL, "") ?: "",
        urbanEndpoint = prefs.getString(KEY_URBAN, "") ?: "",
        crowdEndpoint = prefs.getString(KEY_CROWD_URL, "") ?: ""
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        _state.update { current ->
            val next = transform(current)
            persist(next)
            next
        }
    }

    private fun persist(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS, s.notificationsEnabled)
            .putBoolean(KEY_CROWD, s.externalCrowdAllowed)
            .putBoolean(KEY_OFFLINE, s.offlineOnly)
            .putBoolean(KEY_LEARNING, s.learningMode)
            .putBoolean(KEY_DARK, s.darkMode)
            .putBoolean(KEY_CONSENT, s.consentGiven)
            .putString(KEY_USER, s.userName)
            .putString(KEY_ORG, s.organization)
            .putInt(KEY_INTERVAL, s.agentIntervalSec)
            .putString(KEY_DURATION, s.agentDuration)
            .putInt(KEY_CUSTOM_DAYS, s.agentCustomDays)
            .putBoolean(KEY_DYNAMIC, s.dynamicPriority)
            .putString(KEY_LORA, s.loraEndpoint)
            .putString(KEY_OPTICAL, s.opticalEndpoint)
            .putString(KEY_URBAN, s.urbanEndpoint)
            .putString(KEY_CROWD_URL, s.crowdEndpoint)
            .apply()
    }

    companion object {
        private const val PREFS = "secureguard_settings"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_CROWD = "external_crowd"
        private const val KEY_OFFLINE = "offline_only"
        private const val KEY_LEARNING = "learning_mode"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_CONSENT = "gdpr_consent"
        private const val KEY_USER = "user_name"
        private const val KEY_ORG = "organization"
        private const val KEY_INTERVAL = "agent_interval"
        private const val KEY_DURATION = "agent_duration"
        private const val KEY_CUSTOM_DAYS = "agent_custom_days"
        private const val KEY_DYNAMIC = "dynamic_priority"
        private const val KEY_LORA = "endpoint_lora"
        private const val KEY_OPTICAL = "endpoint_optical"
        private const val KEY_URBAN = "endpoint_urban"
        private const val KEY_CROWD_URL = "endpoint_crowd"
    }
}
