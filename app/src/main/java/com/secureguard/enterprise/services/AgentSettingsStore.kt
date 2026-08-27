package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenter Speicher für die Agent-Einstellungen.
 *
 * Vorher wurden die Einstellungen nur in-memory gehalten: Nach einem
 * App-/Geräteneustart startete der Agent immer mit den hartcodierten
 * Dashboard-Defaults. Jetzt werden [AgentSettings] als JSON in
 * SharedPreferences persistiert und von allen Startpfaden (Dashboard,
 * Agent-Config, Settings, Worker) gelesen.
 */
@Singleton
class AgentSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Gespeicherte Einstellungen oder [AgentSettings]-Defaults. */
    fun load(): AgentSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AgentSettings()
        return try {
            gson.fromJson(json, AgentSettings::class.java) ?: AgentSettings()
        } catch (_: Exception) {
            AgentSettings()
        }
    }

    fun save(settings: AgentSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    companion object {
        private const val PREFS = "secureguard_agent"
        private const val KEY_SETTINGS = "agent_settings_json"
    }
}
