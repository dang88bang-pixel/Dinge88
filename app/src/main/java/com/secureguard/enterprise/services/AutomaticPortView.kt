package com.secureguard.enterprise.services

import com.secureguard.enterprise.config.EndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines einzelnen TCP-Port-Probes. */
enum class PortState { OPEN, CLOSED, TIMEOUT, UNREACHABLE }

/** Ein automatisch abgeleitetes Prüfziel (Host + Port + Herkunft). */
data class PortTarget(
    val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val source: String
)

/** Einzelmessung zu einem [PortTarget]. */
data class PortProbe(
    val target: PortTarget,
    val state: PortState? = null,
    val latencyMs: Long? = null,
    val detail: String = "",
    val probedAt: Long? = null
)

/** Gesamtzustand der automatischen Port-Ansicht (Bridge-Snapshot). */
data class AutomaticPortSnapshot(
    val probing: Boolean = false,
    val checkedAt: Long? = null,
    val probes: List<PortProbe> = emptyList()
) {
    val openCount: Int get() = probes.count { it.state == PortState.OPEN }
    val closedCount: Int get() = probes.count { it.state == PortState.CLOSED }
    val failingCount: Int
        get() = probes.count { it.state == PortState.TIMEOUT || it.state == PortState.UNREACHABLE }
    val pendingCount: Int get() = probes.count { it.state == null }
    val allOk: Boolean
        get() = probes.isNotEmpty() && probes.all { it.state == PortState.OPEN }
}

/**
 * Reine URL-/Stack-Ableitungslogik (JVM, ohne Android-Abhängigkeiten –
 * dadurch im Unit-Test prüfbar).
 */
internal object PortViewEndpoints {

    data class HostPort(val host: String, val port: Int)

    /**
     * Zerlegt eine Endpunkt-URL (oder nackte "host:port"-Angabe) in Host und
     * Port. Fehlt der Port, greift [defaultPort].
     */
    fun parseHostPort(raw: String, defaultPort: Int): HostPort? {
        val input = raw.trim().trimEnd('/')
        if (input.isEmpty()) return null
        return try {
            if (input.contains("://")) {
                val uri = URI(input)
                val host = uri.host?.takeIf { it.isNotBlank() }
                    ?: uri.rawAuthority?.substringBefore(':')?.takeIf { it.isNotBlank() }
                    ?: return null
                val explicit = uri.port
                HostPort(host, if (explicit in 1..65535) explicit else defaultPort)
            } else {
                val idx = input.lastIndexOf(':')
                val maybePort = idx.takeIf { it > 0 }
                    ?.let { input.substring(it + 1).toIntOrNull() }
                when {
                    maybePort != null -> HostPort(input.substring(0, idx), maybePort)
                    else -> HostPort(input, defaultPort)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Standard-Ports des docker-compose-Stacks, die zusätzlich geprüft werden. */
    private val CANONICAL_STACK_PORTS = listOf(
        1883 to "MQTT (TCP)",
        9001 to "MQTT (WebSocket)",
        8000 to "Backend (REST)",
        1880 to "Node-RED"
    )

    /**
     * Ergänzt die bekannten Stack-Ports auf dem [primaryHost], sofern dort
     * nicht bereits durch konfigurierte Endpunkte abgedeckt.
     */
    fun canonicalStackTargets(
        primaryHost: String,
        taken: Collection<HostPort>
    ): List<PortTarget> {
        val host = primaryHost.trim().removePrefix("[").removeSuffix("]")
        if (host.isEmpty()) return emptyList()
        return CANONICAL_STACK_PORTS.mapNotNull { (port, label) ->
            if (taken.any { it.host == host && it.port == port }) null else {
                PortTarget(
                    id = "stack-$port",
                    label = label,
                    host = host,
                    port = port,
                    source = "Standard-Stack (docker-compose)"
                )
            }
        }
    }
}

/**
 * Automatic Port View – native Bridge zur automatischen Verbindungs-Ansicht.
 *
 * Leitet alle Prüfziele **automatisch** aus den konfigurierten Endpunkten ab
 * (MQTT-, WebSocket-, Backend-, MCP-/LoRa-/YOLO-/Find-My-URLs plus die
 * bekannten Stack-Ports aus docker-compose) und misst deren Erreichbarkeit
 * mit nativen TCP-Probes – ohne dass der Anwender Ports eintippen, eine
 * Berechtigung erteilen oder manuell aktualisieren muss.
 *
 * Die Bridge hält ihren letzten Snapshot als [StateFlow]; die UI startet den
 * Auto-Refresh-Zyklus beim Öffnen und stoppt ihn beim Verlassen der Ansicht.
 */
@Singleton
class AutomaticPortView @Inject constructor(
    private val endpointConfig: EndpointConfig
) {

    companion object {
        /** Abstand zwischen zwei automatischen Prüfzyklen. */
        const val AUTO_REFRESH_INTERVAL_MS = 5_000L

        /** Timeout pro TCP-Connect (kurz, damit Ausfälle schnell sichtbar sind). */
        const val CONNECT_TIMEOUT_MS = 1_200L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val _snapshot = MutableStateFlow(AutomaticPortSnapshot())
    val snapshot: StateFlow<AutomaticPortSnapshot> = _snapshot.asStateFlow()

    /**
     * Leitet die Prüfziele aus der EndpointConfig ab – automatisch, ohne
     * manuelle Eingabe. Konfigurierte Endpunkte gewinnen, danach werden die
     * noch fehlenden Standard-Stack-Ports auf dem primären Host ergänzt.
     */
    fun buildTargets(): List<PortTarget> {
        val targets = LinkedHashMap<String, PortTarget>()
        val taken = mutableListOf<PortViewEndpoints.HostPort>()

        fun add(id: String, label: String, rawUrl: String, defaultPort: Int, source: String) {
            val parsed = PortViewEndpoints.parseHostPort(rawUrl, defaultPort)
                ?: return
            targets[id] = PortTarget(
                id = id,
                label = label,
                host = parsed.host,
                port = parsed.port,
                source = source
            )
            taken += parsed
        }

        val mqttUrl = endpointConfig.mqttBrokerUrl
        add("mqtt", "MQTT Broker", mqttUrl, 1883, "MQTT-Einstellung")

        val wsUrl = endpointConfig.websocketUrl
        add("websocket", "WebSocket", wsUrl, 8000, "WebSocket-Einstellung")

        val backendUrl = endpointConfig.backendBaseUrl
        add("backend", "Backend API", backendUrl, 8000, "Backend-Einstellung")

        val mcpUrl = endpointConfig.mcpServerUrl
        add("mcp", "MCP / Slack-Bridge", mcpUrl, 8000, "MCP-Einstellung")

        add("lora", "LoRa-Gateway", endpointConfig.loraGatewayUrl, 1700, "LoRa-Einstellung")
        add("yolo", "YOLO-Server", endpointConfig.yoloServerUrl, 8000, "YOLO-Einstellung")
        add("findmy", "Find-My Proxy", endpointConfig.findMyProxyUrl, 8000, "Find-My-Einstellung")

        // Primärer Host für die Stack-Ports: Backend, sonst WS, sonst MQTT.
        val primaryHost = listOf(backendUrl, wsUrl, mqttUrl)
            .mapNotNull { PortViewEndpoints.parseHostPort(it, 8000)?.host }
            .firstOrNull().orEmpty()

        PortViewEndpoints.canonicalStackTargets(primaryHost, taken).forEach { t ->
            targets[t.id] = t
        }
        return targets.values.toList()
    }

    /** Startet den automatischen Prüfzyklus (idempotent). */
    @Synchronized
    fun startAutoRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            while (isActive) {
                val startedAt = System.nanoTime()
                runProbeCycle()
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                delay((AUTO_REFRESH_INTERVAL_MS - elapsedMs).coerceAtLeast(250L))
            }
        }
    }

    /** Stoppt den automatischen Prüfzyklus (letzter Snapshot bleibt erhalten). */
    @Synchronized
    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Einmalige Sofort-Prüfung (überschreibt den letzten Snapshot). */
    suspend fun probeNow() {
        runProbeCycle()
    }

    private suspend fun runProbeCycle() {
        val last = _snapshot.value
        _snapshot.value = last.copy(probing = true)
        val snapshot = withContext(Dispatchers.IO) { probeAll() }
        _snapshot.value = snapshot
    }

    private fun probeAll(): AutomaticPortSnapshot {
        val targets = buildTargets()
        val now = System.currentTimeMillis()
        val probes = targets.map { probe(it, now) }
        return AutomaticPortSnapshot(probing = false, checkedAt = now, probes = probes)
    }

    private fun probe(target: PortTarget, now: Long): PortProbe {
        val startedAt = System.nanoTime()
        val host = target.host.removePrefix("[").removeSuffix("]")
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, target.port), CONNECT_TIMEOUT_MS.toInt())
            val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
            val detail = if (target.id == "backend") backendHttpDetail() else ""
            PortProbe(
                target = target,
                state = PortState.OPEN,
                latencyMs = latencyMs,
                detail = detail,
                probedAt = now
            )
        } catch (e: UnknownHostException) {
            PortProbe(target, PortState.UNREACHABLE, null, "Unbekannter Host: ${e.message}", now)
        } catch (e: SocketTimeoutException) {
            PortProbe(target, PortState.TIMEOUT, null, "Zeitüberschreitung (${CONNECT_TIMEOUT_MS} ms)", now)
        } catch (e: ConnectException) {
            PortProbe(target, PortState.CLOSED, null, "Verbindung abgelehnt: ${e.message}", now)
        } catch (e: Exception) {
            PortProbe(target, PortState.UNREACHABLE, null, e.message ?: "nicht erreichbar", now)
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** Zusatzprüfung: HTTP-Health des Backends (liefert Dienste-Status). */
    private fun backendHttpDetail(): String {
        val base = endpointConfig.backendBaseUrl.trimEnd('/')
        if (base.isEmpty()) return ""
        return try {
            val request = Request.Builder().url("$base/api/health").get().build()
            http.newCall(request).execute().use { resp ->
                val snippet = resp.body?.string()
                    ?.replace('\n', ' ')
                    ?.take(72)
                    .orEmpty()
                "HTTP ${resp.code}${if (snippet.isBlank()) "" else " · $snippet"}"
            }
        } catch (e: Exception) {
            "TCP offen, HTTP nicht erreichbar (${e.message ?: "Fehler"})"
        }
    }
}
