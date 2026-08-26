package com.secureguard.enterprise.services

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optischer Erkennungskanal:
 * 1) QR/Barcode-Match (ScanQrScreen → [setScannedCode])
 * 2) optional YOLO-Inferenz-Server (`YOLO_SERVER_URL`) mit letztem Kamerabild
 *
 * Ohne Scan/Bild und ohne Server → null (keine Simulation).
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val endpointConfig: EndpointConfig
) : DetectionCapable() {

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Letzter gescannter Code (QR/Barcode). */
    @Volatile
    var lastScannedCode: String? = null

    /** Optionales JPEG/PNG der letzten Kameraufnahme für YOLO. */
    @Volatile
    var lastImageJpeg: ByteArray? = null

    suspend fun searchAsset(asset: Asset): Detection? {
        matchQr(asset)?.let { return it }
        return queryYolo(asset)
    }

    private fun matchQr(asset: Asset): Detection? {
        val code = lastScannedCode ?: return null
        val matches = code.equals(asset.mac, ignoreCase = true) ||
            code.equals(asset.id, ignoreCase = true) ||
            (asset.vin != null && code.equals(asset.vin, ignoreCase = true)) ||
            code.equals(asset.shortName, ignoreCase = true) ||
            code.equals(asset.name, ignoreCase = true)
        if (!matches) return null
        lastScannedCode = null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = "optical-qr",
            rssi = 0,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 2f,
            message = "Optisch erkannt (QR): $code",
            timestamp = Date()
        ).also { emit(it) }
    }

    /**
     * Ruft den YOLO-Server auf: POST { image_b64, labels? }.
     * Erwartete Antwort: { "detections": [ { "label": "...", "confidence": 0.9, "lat":.., "lng":.. } ] }
     * Treffer, wenn label MAC/Name/VIN des Assets matched.
     */
    private fun queryYolo(asset: Asset): Detection? {
        val base = endpointConfig.yoloServerUrl
        val image = lastImageJpeg
        if (base.isBlank() || image == null || image.isEmpty()) return null
        return try {
            val b64 = Base64.encodeToString(image, Base64.NO_WRAP)
            val payload = gson.toJson(
                mapOf(
                    "image_b64" to b64,
                    "hints" to listOf(asset.mac, asset.shortName, asset.name, asset.vin).filterNotNull()
                )
            )
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$base/api/v1/detect")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                val result = gson.fromJson(text, YoloResponse::class.java) ?: return null
                val hit = result.detections.firstOrNull { d ->
                    val label = d.label.orEmpty()
                    label.equals(asset.mac, true) ||
                        label.equals(asset.shortName, true) ||
                        label.equals(asset.name, true) ||
                        (asset.vin != null && label.equals(asset.vin, true)) ||
                        label.contains(asset.shortName, true)
                } ?: return null
                // Bild einmalig verbrauchen
                lastImageJpeg = null
                Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.OPTICAL,
                    nodeId = "yolo-server",
                    rssi = 0,
                    latitude = hit.latitude ?: asset.latitude,
                    longitude = hit.longitude ?: asset.longitude,
                    accuracyMeters = hit.accuracy ?: 5f,
                    message = "YOLO: ${hit.label} (${"%.0f".format((hit.confidence ?: 0.0) * 100)}%)",
                    timestamp = Date()
                ).also { emit(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setScannedCode(code: String) {
        lastScannedCode = code
    }

    fun setImageJpeg(bytes: ByteArray) {
        lastImageJpeg = bytes
    }
}

data class YoloResponse(
    @SerializedName("detections") val detections: List<YoloDetection> = emptyList()
)

data class YoloDetection(
    @SerializedName("label") val label: String? = null,
    @SerializedName("confidence") val confidence: Double? = null,
    @SerializedName("lat") val latitude: Double? = null,
    @SerializedName("lng") val longitude: Double? = null,
    @SerializedName("accuracy") val accuracy: Float? = null
)
