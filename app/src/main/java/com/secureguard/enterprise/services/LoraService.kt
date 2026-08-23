package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic LoRa / LoRaWAN service — replaces the former Meshtastic dependency.
 *
 * Real backend integration (no simulation):
 *  - Sightings: HTTP-Abfrage des konfigurierten LoRaWAN-Backends
 *    (`LORA_BACKEND_URL`, z. B. das mitgelieferte FastAPI-Backend aus
 *    `backend/main.py`, das LoRa-Uplinks der Gateways per MQTT einsammelt).
 *    Ohne konfigurierte URL wird ehrlich `null` geliefert.
 *  - Commands: echte MQTT-Zustellung auf `secureguard/<MAC>/command` —
 *    genau das Topic, das die ESP32-Gateway-Firmware abonniert.
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttService: MqttService
) : DetectionCapable() {

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Backend-Endpunkt (leer → Kanal inaktiv, liefert keine Treffer). */
    val isConfigured: Boolean get() = BuildConfig.LORA_BACKEND_URL.isNotBlank()

    /** Last known gateways reported by the backend. */
    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    suspend fun searchAsset(asset: Asset): Detection? {
        val rows = fetchUplinks(asset.mac)
        val latest = rows.maxByOrNull { it.timestamp ?: Date(0) } ?: return null
        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.LORA,
            nodeId = latest.nodeId ?: "lora-gw",
            rssi = latest.rssi,
            latitude = latest.latitude,
            longitude = latest.longitude,
            accuracyMeters = 25f,
            message = "LoRa-Uplink via Gateway ${latest.nodeId ?: "?"}",
            timestamp = latest.timestamp ?: Date()
        )
        emit(detection)
        return detection
    }

    /**
     * Sends a command to an asset reachable via LoRa: echte MQTT-Zustellung
     * über das Gateway-Command-Topic (Firmware: `secureguard/+/command`).
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        mqttService.sendCommand(mac, command)
        return mqttService.isConnected
    }

    suspend fun refreshGateways(): List<Gateway> {
        gateways = fetchGateways()
        return gateways
    }

    // ============ HTTP-INTEGRATION ============

    private suspend fun fetchUplinks(mac: String): List<LoraUplink> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext emptyList()
            // Netzwerk-Retry mit Backoff (flüchtige Ausfälle des Backends abfedern).
            com.secureguard.enterprise.util.RetryManager.withRetryOrNull(
                maxAttempts = 2,
                baseDelayMs = 500
            ) {
                val url = "${BuildConfig.LORA_BACKEND_URL.trimEnd('/')}/api/detections" +
                    "?mac=${mac.uppercase()}&source_type=LORA&limit=20"
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@withRetryOrNull emptyList()
                    val body = resp.body?.string() ?: return@withRetryOrNull emptyList()
                    val type = object : TypeToken<List<LoraUplinkRow>>() {}.type
                    val rows: List<LoraUplinkRow> = gson.fromJson(body, type)
                    rows.map { row ->
                        LoraUplink(
                            nodeId = row.node_id,
                            rssi = row.rssi ?: 0,
                            latitude = row.latitude,
                            longitude = row.longitude,
                            timestamp = row.parseTimestamp()
                        )
                    }
                }
            } ?: emptyList()
        }

    private suspend fun fetchGateways(): List<Gateway> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext emptyList()
            com.secureguard.enterprise.util.RetryManager.withRetryOrNull(
                maxAttempts = 2,
                baseDelayMs = 500
            ) {
                val url = "${BuildConfig.LORA_BACKEND_URL.trimEnd('/')}/api/detections" +
                    "?source_type=LORA&limit=200"
                val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@withRetryOrNull emptyList()
                    val body = resp.body?.string() ?: return@withRetryOrNull emptyList()
                    val type = object : TypeToken<List<LoraUplinkRow>>() {}.type
                    val rows: List<LoraUplinkRow> = gson.fromJson(body, type)
                    rows.groupBy { it.node_id ?: "lora-gw" }
                        .map { (node, hits) ->
                            Gateway(
                                id = node,
                                rssi = hits.maxOfOrNull { it.rssi ?: 0 } ?: 0,
                                latitude = hits.lastOrNull { it.latitude != null }?.latitude ?: 0.0,
                                longitude = hits.lastOrNull { it.longitude != null }?.longitude ?: 0.0,
                                seenMacs = hits.mapNotNull { it.asset_mac?.uppercase() }.distinct()
                            )
                        }
                }
            } ?: emptyList()
        }
}

/** A LoRa gateway that recently reported sightings. */
data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val seenMacs: List<String>
)

private data class LoraUplink(
    val nodeId: String?,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Date?
)

/** JSON-Zeile des Backend-Endpunkts `/api/detections` (Snake-Case). */
private data class LoraUplinkRow(
    val asset_mac: String?,
    val node_id: String?,
    val rssi: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: String?
)

private fun LoraUplinkRow.parseTimestamp(): Date? = try {
    java.text.SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss",
        java.util.Locale.US
    ).parse(timestamp ?: return null)
} catch (e: Exception) {
    null
}
