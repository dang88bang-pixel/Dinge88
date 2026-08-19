package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fragt einen konfigurierbaren Backend-Endpunkt (Pilot) nach einer Detektion ab.
 *
 * Erwartetes Antwortformat (JSON):
 * ```
 * { "found": true, "nodeId": "gw-1", "rssi": -95,
 *   "latitude": 51.2, "longitude": 6.8 }
 * ```
 *
 * Wenn kein Endpunkt konfiguriert ist oder die Antwort fehlschlägt, wird
 * fehlertolerant `null` zurückgegeben (kein Crash, keine Endlosschleife).
 */
@Singleton
class RemoteDetectionFetcher @Inject constructor(
    private val client: OkHttpClient
) {
    /**
     * @param baseUrl  z. B. "https://gw.example.com" (leer = deaktiviert)
     * @param path     z. B. "api/v1/detect"
     */
    suspend fun fetch(
        baseUrl: String,
        path: String,
        mac: String,
        source: DetectionSource
    ): Detection? {
        if (baseUrl.isBlank()) return null

        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append('/')
            append(path.trimStart('/'))
            append("?mac=")
            append(java.net.URLEncoder.encode(mac, "UTF-8"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    parseDetection(body, mac, source)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseDetection(
        json: String,
        mac: String,
        source: DetectionSource
    ): Detection? {
        return try {
            val obj = JSONObject(json)
            if (!obj.optBoolean("found", false)) return null
            Detection(
                assetMac = mac,
                sourceType = source,
                nodeId = obj.optString("nodeId", mac),
                rssi = obj.optInt("rssi", 0),
                latitude = optNullableDouble(obj, "latitude"),
                longitude = optNullableDouble(obj, "longitude"),
                timestamp = Date()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun optNullableDouble(obj: JSONObject, key: String): Double? {
        return if (obj.has(key) && !obj.isNull(key)) {
            runCatching { obj.getDouble(key) }.getOrNull()
        } else {
            null
        }
    }
}
