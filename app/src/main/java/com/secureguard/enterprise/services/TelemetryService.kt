package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.JsonParser
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liest Telemetrie (Batterie, Motor, Position, ...) von Assets über den
 * echten MQTT-Kanal (Broker: [MqttConfig], Gateways: ESP32-Firmware aus
 * `firmware/`) und sendet Befehle zurück.
 *
 * Ablauf: `TELEMETRY_READ` wird auf dem Befehl-Topic des Assets publiziert;
 * das Gateway/der Endpunkt antwortet auf dem Telemetrie-Topic
 * (`secureguard/<MAC>/telemetry`), woraufhin [searchAsset] die echte
 * Antwort als [Telemetry] liefert. Parallel werden laufende Telemetrie-
 * Events des Brokers gemerkt. Ohne Broker/Antwort → `null`
 * (keine simulierten Messwerte).
 */
@Singleton
class TelemetryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttService: MqttService
) : DetectionCapable() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val latest = mutableMapOf<String, Telemetry>()
    private val mutex = Mutex()
    private var collectorJob: Job? = null

    init {
        // Laufende echte Telemetrie-Events vom MQTT-Broker sammeln.
        collectorJob = scope.launch {
            mqttService.events.collect { event ->
                if (event is MqttEvent.Telemetry) {
                    val telemetry = telemetryFrom(event)
                    mutex.withLock { latest[telemetry.mac.uppercase()] = telemetry }
                    emit(
                        Detection(
                            assetMac = telemetry.mac,
                            sourceType = DetectionSource.TELEMETRY,
                            nodeId = "mqtt-telemetry",
                            rssi = event.rssi,
                            latitude = telemetry.latitude,
                            longitude = telemetry.longitude,
                            accuracyMeters = 30f,
                            timestamp = telemetry.timestamp
                        )
                    )
                }
            }
        }
    }

    /**
     * Echter Telemetrie-Auslese: `TELEMETRY_READ` per MQTT publizieren und
     * bis zu [REQUEST_TIMEOUT_MS] auf die echte Antwort warten. Fallback:
     * zuletzt gemeldete Telemetrie dieses Assets.
     */
    suspend fun searchAsset(asset: Asset): Detection? {
        val requested = requestTelemetry(asset.mac)
        val telemetry = requested ?: mutex.withLock { latest[asset.mac.uppercase()] }
        if (telemetry == null) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.TELEMETRY,
            nodeId = "mqtt-telemetry",
            rssi = 0,
            latitude = telemetry.latitude,
            longitude = telemetry.longitude,
            accuracyMeters = 30f,
            timestamp = telemetry.timestamp
        ).also { emit(it) }
    }

    suspend fun getLatestTelemetry(mac: String): Telemetry? = mutex.withLock {
        latest[mac.uppercase()]
    }

    /**
     * Sendet einen Befehl an das Asset über den echten MQTT-Channel
     * (Topic `secureguard/<MAC>/command`). Liefert `false`, wenn kein
     * Broker verbunden ist.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        if (!mqttService.isConnected) return false
        mqttService.sendCommand(mac, command)
        return true
    }

    /** Leert den Telemetrie-Cache (z. B. beim Logout). */
    suspend fun clear() = mutex.withLock { latest.clear() }

    override fun onCleared() {
        super.onCleared()
        collectorJob?.cancel()
        collectorJob = null
    }

    // ============ INTERN ============

    private suspend fun requestTelemetry(mac: String): Telemetry? {
        if (!mqttService.isConnected) return null
        val deferred = CompletableDeferred<Telemetry?>()
        val collector = scope.launch {
            mqttService.events.collect { event ->
                if (event is MqttEvent.Telemetry &&
                    event.assetMac.equals(mac, ignoreCase = true) &&
                    !deferred.isCompleted
                ) {
                    deferred.complete(telemetryFrom(event))
                }
            }
        }
        mqttService.sendCommand(mac, "TELEMETRY_READ")
        val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
        collector.cancel()
        return result
    }

    /** Parst eine echte MQTT-Telemetrie-Nachricht in [Telemetry]. */
    private fun telemetryFrom(event: MqttEvent.Telemetry): Telemetry {
        val json = runCatching { JsonParser.parseString(event.payload) }
            .getOrNull()?.asJsonObject
        fun intOrNull(key: String): Int? =
            json?.get(key)?.takeIf { it.isNumber }?.asInt
        fun doubleOrNull(key: String): Double? =
            json?.get(key)?.takeIf { it.isNumber }?.asDouble
        fun boolOr(key: String, default: Boolean): Boolean =
            json?.get(key)?.takeIf { it.isBoolean }?.asBoolean ?: default

        return Telemetry(
            mac = json?.get("mac")?.takeIf { !it.isJsonNull }?.asString
                ?: event.assetMac,
            batteryPercent = intOrNull("battery"),
            fuelPercent = intOrNull("fuel"),
            motorOk = boolOr("motor_ok", true),
            tiresOk = boolOr("tires_ok", true),
            operatingHours = doubleOrNull("operating_hours"),
            kilometers = doubleOrNull("km") ?: doubleOrNull("kilometers"),
            latitude = event.latitude ?: doubleOrNull("lat"),
            longitude = event.longitude ?: doubleOrNull("lng"),
            timestamp = Date()
        )
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 3_000L
    }
}
