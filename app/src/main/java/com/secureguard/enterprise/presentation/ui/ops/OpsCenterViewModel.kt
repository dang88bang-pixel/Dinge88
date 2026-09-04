package com.secureguard.enterprise.presentation.ui.ops

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.PendingAction
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettingsStore
import com.secureguard.enterprise.services.AgentStatus
import com.secureguard.enterprise.services.OfflineQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Datenversorgung des 3D Operations Center.
 *
 * Hält einen jederzeit lesbaren JSON-Schnappschuss des Live-Zustands bereit
 * (Assets, Alarme, Detektionen, Agent, Offline-Queue). Die WebView-Brücke
 * liest ihn synchron aus – dadurch bleibt die JavaScript-Seite frei von
 * Callback-Ketten und der Renderloop ruckelfrei.
 */
@HiltViewModel
class OpsCenterViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val roleManager: RoleManager,
    private val offlineQueue: OfflineQueue,
    private val agentSettingsStore: AgentSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    @Volatile
    private var snapshot: String = EMPTY_SNAPSHOT

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Kennzahl für die Kopfzeile des Screens (ohne WebView-Abhängigkeit). */
    val assetCount: StateFlow<Int> = repository.getWhitelistedAssets()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            combine(
                repository.getWhitelistedAssets(),
                repository.getAlerts(),
                repository.getAllDetections(),
                agentService.agentStatus,
                offlineQueue.pending
            ) { assets, alerts, detections, agent, pending ->
                buildSnapshot(assets, alerts, detections, agent, pending)
            }.collect { json ->
                snapshot = json
                _ready.value = true
            }
        }
    }

    /** Synchroner Zugriff für die JavaScript-Brücke. */
    fun snapshotJson(): String = snapshot

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) {
            agentService.stop()
            runCatching {
                context.stopService(android.content.Intent(context, AgentForegroundService::class.java))
            }
        } else {
            agentService.start(agentSettingsStore.load())
        }
    }

    fun acknowledgeAlert(id: Long) {
        viewModelScope.launch { runCatching { repository.acknowledgeAlert(id) } }
    }

    /**
     * Führt einen Befehl über den echten Zustellweg aus
     * (MQTT → WebSocket → BLE/GATT → Offline-Queue) und meldet das Ergebnis
     * über [onResult] zurück an die 3D-Konsole.
     */
    fun execute(
        assetId: String,
        wireCommand: String,
        note: String,
        onResult: (state: String, detail: String) -> Unit
    ) {
        viewModelScope.launch {
            if (!roleManager.require(Permission.EXECUTE_ACTIONS)) {
                onResult("denied", "Rolle ${roleManager.currentRole} ohne Recht EXECUTE_ACTIONS")
                return@launch
            }
            val asset = runCatching { repository.getAssetById(assetId) }.getOrNull()
                ?: runCatching { repository.resolveAsset(assetId) }.getOrNull()
            if (asset == null) {
                onResult("blocked", "Asset nicht gefunden")
                return@launch
            }
            val command = if (note.isBlank()) wireCommand else "$wireCommand:${note.take(120)}"
            val delivered = runCatching { agentService.sendAction(asset, command) }
                .getOrElse { error ->
                    onResult("blocked", error.message ?: "Zustellfehler")
                    return@launch
                }
            if (delivered) onResult("delivered", "Direktkanal")
            else onResult("queued", "Offline-Queue")
        }
    }

    fun flushQueue(onResult: (state: String, detail: String) -> Unit) {
        viewModelScope.launch {
            val sent = runCatching { agentService.flushOfflineQueue() }.getOrDefault(0)
            onResult(if (sent > 0) "delivered" else "blocked", sent.toString())
        }
    }

    /* ------------------------------------------------------------------ */
    /* JSON-Aufbereitung                                                   */
    /* ------------------------------------------------------------------ */

    private fun buildSnapshot(
        assets: List<Asset>,
        alerts: List<Alert>,
        detections: List<Detection>,
        agent: AgentStatus,
        pending: List<PendingAction>
    ): String {
        val macToId = assets.associate { it.mac to it.id }
        val macToName = assets.associate { it.mac to it.shortName }

        val root = JSONObject()
        root.put("source", "native")
        root.put("queue", pending.size)
        root.put("role", roleManager.currentRole.name)
        root.put("canExecute", roleManager.has(Permission.EXECUTE_ACTIONS))

        root.put("agent", JSONObject().apply {
            put("running", agent.running)
            put("cycle", agent.cycle)
            put("startedAt", agent.startedAt ?: JSONObject.NULL)
            put("intervalSec", agent.settings.interval)
            put("lastRunAt", agent.lastRunAt ?: JSONObject.NULL)
        })

        root.put("assets", JSONArray().apply {
            assets.forEach { asset ->
                put(JSONObject().apply {
                    put("id", asset.id)
                    put("name", asset.name)
                    put("shortName", asset.shortName)
                    put("kind", "asset")
                    put("mac", asset.mac)
                    put("status", asset.status.name)
                    put("rssi", asset.rssi)
                    put("battery", asset.batteryLevel ?: JSONObject.NULL)
                    put("lat", asset.latitude ?: JSONObject.NULL)
                    put("lon", asset.longitude ?: JSONObject.NULL)
                    put("lastSeen", asset.lastSeen?.time ?: JSONObject.NULL)
                    put("maintenanceDue", asset.maintenanceDue)
                })
            }
        })

        root.put("alerts", JSONArray().apply {
            alerts.take(MAX_ALERTS).forEach { alert ->
                put(JSONObject().apply {
                    put("id", alert.id.toString())
                    put("assetId", alert.assetId)
                    put("assetName", alert.assetId)
                    put("type", alert.type.name)
                    put("severity", alert.severity.name)
                    put("message", alert.message)
                    put("acknowledged", alert.acknowledged)
                    put("ts", alert.timestamp.time)
                })
            }
        })

        root.put("detections", JSONArray().apply {
            detections.take(MAX_DETECTIONS).forEach { detection ->
                put(JSONObject().apply {
                    put("id", detection.id.toString())
                    put("assetId", macToId[detection.assetMac] ?: detection.assetMac)
                    put("assetName", macToName[detection.assetMac] ?: detection.assetMac)
                    put("source", detection.sourceType.name)
                    put("rssi", detection.rssi)
                    put("lat", detection.latitude ?: JSONObject.NULL)
                    put("lon", detection.longitude ?: JSONObject.NULL)
                    put("ts", detection.timestamp.time)
                })
            }
        })

        return root.toString()
    }

    private companion object {
        const val MAX_ALERTS = 60
        const val MAX_DETECTIONS = 120
        const val EMPTY_SNAPSHOT =
            """{"source":"native","queue":0,"agent":{"running":false,"cycle":0},""" +
                """"assets":[],"alerts":[],"detections":[]}"""
    }
}
