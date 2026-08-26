package com.secureguard.enterprise.services

import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregiert den Betriebszustand von App-Kanälen und Backend-Health
 * für Dashboard / Settings (Monitoring ops).
 */
@Singleton
class HealthMonitorService @Inject constructor(
    private val endpointConfig: EndpointConfig,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val agentService: AgentService,
    private val database: SecureGuardDatabase,
    private val apiNodeManager: ApiNodeManager,
    private val backendSyncService: BackendSyncService
) {

    data class ComponentHealth(
        val id: String,
        val label: String,
        val ok: Boolean,
        val detail: String = ""
    )

    data class SystemHealth(
        val checkedAt: Long = System.currentTimeMillis(),
        val components: List<ComponentHealth> = emptyList(),
        val assetCount: Int = 0,
        val detectionCount: Int = 0,
        val openAlerts: Int = 0,
        val agentRunning: Boolean = false,
        val agentCycle: Long = 0
    ) {
        val allCriticalOk: Boolean
            get() = components.filter { it.id in CRITICAL }.all { it.ok }

        companion object {
            private val CRITICAL = setOf("db", "agent")
        }
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    suspend fun snapshot(): SystemHealth = withContext(Dispatchers.IO) {
        val components = mutableListOf<ComponentHealth>()

        // DB
        val assetCount = runCatching { database.assetDao().count() }.getOrDefault(-1)
        val detectionCount = runCatching { database.detectionDao().count() }.getOrDefault(-1)
        val openAlerts = runCatching {
            database.alertDao().observeUnacknowledgedCount().first()
        }.getOrDefault(0)
        components += ComponentHealth(
            id = "db",
            label = "Room/SQLCipher",
            ok = assetCount >= 0,
            detail = if (assetCount >= 0) "$assetCount Assets · $detectionCount Detektionen" else "DB-Fehler"
        )

        // Agent
        val agent = agentService.agentStatus.value
        components += ComponentHealth(
            id = "agent",
            label = "Agent",
            ok = true,
            detail = if (agent.running) {
                "läuft · Zyklus ${agent.cycle} · last=${agent.lastRunAt ?: "–"}"
            } else {
                "gestoppt"
            }
        )

        // MQTT
        val mqttUrl = endpointConfig.mqttBrokerUrl
        components += ComponentHealth(
            id = "mqtt",
            label = "MQTT",
            ok = mqttService.isConnected || mqttUrl.isNotBlank(),
            detail = when {
                mqttService.isConnected -> "verbunden · $mqttUrl"
                mqttUrl.isNotBlank() -> "konfiguriert, nicht verbunden · $mqttUrl"
                else -> "keine URL"
            }
        )

        // WebSocket
        val wsUrl = endpointConfig.websocketUrl
        components += ComponentHealth(
            id = "websocket",
            label = "WebSocket",
            ok = wsUrl.isNotBlank(),
            detail = wsUrl.ifBlank { "nicht gesetzt" }
        )

        // Backend HTTP health
        val base = endpointConfig.backendBaseUrl
        if (base.isNotBlank()) {
            val health = probeHttp("$base/api/health")
            components += ComponentHealth(
                id = "backend",
                label = "Backend API",
                ok = health.first,
                detail = if (health.first) "OK · $base (${health.second})" else "Fehler · $base · ${health.second}"
            )
        } else {
            components += ComponentHealth(
                id = "backend",
                label = "Backend API",
                ok = false,
                detail = "BACKEND_BASE_URL nicht gesetzt"
            )
        }

        // Gateways (optional)
        listOf(
            "lora" to endpointConfig.loraGatewayUrl,
            "yolo" to endpointConfig.yoloServerUrl,
            "findmy" to endpointConfig.findMyProxyUrl
        ).forEach { (id, url) ->
            if (url.isNotBlank()) {
                components += ComponentHealth(
                    id = id,
                    label = id.uppercase(),
                    ok = true,
                    detail = url
                )
            }
        }

        // API nodes
        val nodes = runCatching { apiNodeManager.nodeStatus.value.size }.getOrDefault(0)
        components += ComponentHealth(
            id = "nodes",
            label = "API-Nodes",
            ok = nodes > 0,
            detail = "$nodes Knoten"
        )

        components += ComponentHealth(
            id = "sync",
            label = "Backend-Sync",
            ok = backendSyncService.isConfigured,
            detail = if (backendSyncService.isConfigured) "konfiguriert" else "ohne Backend-URL"
        )

        SystemHealth(
            components = components,
            assetCount = assetCount.coerceAtLeast(0),
            detectionCount = detectionCount.coerceAtLeast(0),
            openAlerts = openAlerts,
            agentRunning = agent.running,
            agentCycle = agent.cycle
        )
    }

    private fun probeHttp(url: String): Pair<Boolean, String> {
        return try {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.take(80).orEmpty()
                resp.isSuccessful to "HTTP ${resp.code} $body"
            }
        } catch (e: Exception) {
            false to (e.message ?: "unreachable")
        }
    }
}
