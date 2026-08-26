package com.secureguard.enterprise.services

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt und verwaltet die MQTT-Zugangsdaten DIREKT IN DER APP.
 *
 * Die Passwörter werden beim ersten Zugriff mit [SecureRandom] in der App
 * erzeugt (24 Zeichen, A–Z/a–z/0–9) und lokal gespeichert – es ist KEIN
 * externes Setup-File und KEINE Nutzungseinschränkung nötig. Der Broker
 * akzeptiert diese app-erzeugten Zugangsdaten (siehe scripts/mqtt-broker.js).
 *
 * Benutzername: `sg-<Geräte-ID>` (nur MQTT-erlaubte Zeichen [a-zA-Z0-9]).
 *
 * Die Werte werden über dieselben SharedPreferences-Keys gespeichert, die
 * [ServiceEndpoints] liest – damit nutzt [MqttService] sie automatisch.
 */
@Singleton
class MqttCredentialManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(ServiceEndpoints.PREFS_NAME, Context.MODE_PRIVATE)

    private val random = SecureRandom()

    /** Eindeutiger, MQTT-konformer Benutzername (bleibt über Installationen gleich). */
    val username: String
        get() = prefs.getString(ServiceEndpoints.KEY_MQTT_USER, null)
            ?: run {
                val deviceId = Settings.Secure.getString(
                    appContext.contentResolver, Settings.Secure.ANDROID_ID
                ) ?: "unknown"
                val clean = deviceId.filter { it.isLetterOrDigit() }.take(20)
                "sg-${clean.ifEmpty { "device" }}"
            }

    /**
     * Erzeugt die Zugangsdaten beim ersten Aufruf in der App (idempotent).
     * Liefert `true`, wenn neu erzeugt, `false`, wenn bereits vorhanden.
     */
    fun ensureCredentials(): Boolean {
        val existing = prefs.getString(ServiceEndpoints.KEY_MQTT_PASS, null)
        if (!existing.isNullOrBlank()) return false
        prefs.edit()
            .putString(ServiceEndpoints.KEY_MQTT_USER, username)
            .putString(ServiceEndpoints.KEY_MQTT_PASS, generatePassword())
            .apply()
        return true
    }

    /** Erzeugt ein NEUES Passwort und speichert es (Benutzername bleibt). */
    fun regenerate(): String {
        val pass = generatePassword()
        prefs.edit()
            .putString(ServiceEndpoints.KEY_MQTT_USER, username)
            .putString(ServiceEndpoints.KEY_MQTT_PASS, pass)
            .apply()
        return pass
    }

    /** Aktuelles Passwort (null, wenn noch keins erzeugt wurde). */
    val password: String?
        get() = prefs.getString(ServiceEndpoints.KEY_MQTT_PASS, null)

    private fun generatePassword(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(24) {
            repeat(24) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }
}
