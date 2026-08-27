package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirektionaler Sync zwischen lokaler Room-DB und SecureGuard-Backend.
 *
 * - [pullAssets]: GET /api/assets → upsert lokal
 * - [pushAsset]: POST /api/assets für ein Asset
 * - [pushAllWhitelisted]: alle Whitelist-Assets hochladen
 */
@Singleton
class BackendSyncService @Inject constructor(
    private val endpointConfig: EndpointConfig,
    private val repository: SecureGuardRepository
) {

    private val gson = Gson()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val isConfigured: Boolean get() = endpointConfig.backendBaseUrl.isNotBlank()

    data class SyncResult(
        val pulled: Int = 0,
        val pushed: Int = 0,
        val errors: List<String> = emptyList()
    )

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext SyncResult(errors = listOf("BACKEND_BASE_URL / WEBSOCKET_URL nicht gesetzt"))
        }
        val errors = mutableListOf<String>()
        val pulled = runCatching { pullAssets() }
            .onFailure { errors += "Pull: ${it.message}" }
            .getOrDefault(0)
        val pushed = runCatching { pushAllWhitelisted() }
            .onFailure { errors += "Push: ${it.message}" }
            .getOrDefault(0)
        SyncResult(pulled = pulled, pushed = pushed, errors = errors)
    }

    suspend fun pullAssets(): Int = withContext(Dispatchers.IO) {
        val base = endpointConfig.backendBaseUrl
        if (base.isBlank()) return@withContext 0
        val request = Request.Builder().url("$base/api/assets").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val type = object : TypeToken<List<RemoteAsset>>() {}.type
            val remote: List<RemoteAsset> = gson.fromJson(body, type) ?: emptyList()
            var count = 0
            remote.forEach { r ->
                val mac = r.mac?.uppercase() ?: return@forEach
                val id = r.id?.ifBlank { null } ?: "remote-$mac"
                val existing = repository.getAssetByMac(mac) ?: repository.getAssetById(id)
                val asset = Asset(
                    id = existing?.id ?: id,
                    name = r.name ?: existing?.name ?: mac,
                    shortName = r.shortName ?: existing?.shortName ?: (r.name ?: mac).take(16),
                    mac = mac,
                    status = parseStatus(r.status) ?: existing?.status ?: AssetStatus.UNKNOWN,
                    rssi = r.rssi ?: existing?.rssi ?: 0,
                    latitude = r.latitude ?: existing?.latitude,
                    longitude = r.longitude ?: existing?.longitude,
                    lastSeen = parseDate(r.lastSeen) ?: existing?.lastSeen,
                    whitelisted = existing?.whitelisted ?: true,
                    externalAllowed = existing?.externalAllowed ?: false,
                    maintenanceDue = existing?.maintenanceDue ?: false,
                    notes = existing?.notes,
                    createdAt = existing?.createdAt ?: Date(),
                    updatedAt = Date()
                )
                repository.upsertAsset(asset)
                count++
            }
            count
        }
    }

    suspend fun pushAsset(asset: Asset): Boolean = withContext(Dispatchers.IO) {
        val base = endpointConfig.backendBaseUrl
        if (base.isBlank()) return@withContext false
        val payload = gson.toJson(
            mapOf(
                "id" to asset.id,
                "name" to asset.name,
                "mac" to asset.mac,
                "short_name" to asset.shortName,
                "status" to asset.status.name,
                "latitude" to asset.latitude,
                "longitude" to asset.longitude,
                "rssi" to asset.rssi,
                "last_seen" to asset.lastSeen?.let { isoFormat.format(it) }
            )
        )
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$base/api/assets")
            // F-71: Backend verlangt X-API-Key auf schreibenden Endpunkten
            // (Header nur setzen, wenn Key konfiguriert ist)
            .apply {
                val key = endpointConfig.backendApiKey
                if (key.isNotBlank()) header("X-API-Key", key)
            }
            .post(body)
            .build()
        http.newCall(request).execute().use { it.isSuccessful }
    }

    suspend fun pushAllWhitelisted(): Int = withContext(Dispatchers.IO) {
        // Flow.first would need collect; use getAll and filter
        var n = 0
        // Repository has getAllAssets as Flow – pull once via runBlocking-safe API
        // We expose a one-shot through a small helper:
        val assets = repository.snapshotWhitelisted()
        assets.forEach { a ->
            if (pushAsset(a)) n++
        }
        n
    }

    private fun parseStatus(raw: String?): AssetStatus? =
        raw?.let { runCatching { AssetStatus.valueOf(it.uppercase()) }.getOrNull() }

    private fun parseDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return runCatching { isoFormat.parse(raw) }.getOrNull()
            ?: runCatching { isoFormatNoMs.parse(raw) }.getOrNull()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val isoFormatNoMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

data class RemoteAsset(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("mac") val mac: String? = null,
    @SerializedName("short_name") val shortName: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
)
