package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.secureguard.enterprise.config.EndpointConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slack-Anbindung über das SecureGuard-Backend.
 *
 * Das Backend ist MCP-Client für den Slack-MCP-Server
 * (provectus/slack-mcp-server) und kapselt JSON-RPC hinter REST:
 *
 * - `GET  /api/slack/health`   → Erreichbarkeit, Tools, Ziel-Channel
 * - `GET  /api/slack/tools`    → registrierte MCP-Tools
 * - `GET  /api/slack/channels` → Channel-Verzeichnis
 * - `POST /api/slack/call`     → beliebiger Tool-Aufruf      (X-API-Key)
 * - `POST /api/slack/notify`   → Meldung in einen Channel    (X-API-Key)
 *
 * Die App spricht deshalb kein MCP/SSE selbst – Konfiguration und Tokens
 * bleiben vollständig im Backend (Details: `docs/SLACK_MCP.md`).
 */
@Singleton
class SlackService @Inject constructor(
    private val endpointConfig: EndpointConfig
) {

    private val gson = Gson()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // MCP-Aufrufe (History/Suche) können länger dauern als ein REST-Get.
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Backend-URL gesetzt? Ohne sie sind alle Aufrufe no-ops. */
    val isConfigured: Boolean get() = endpointConfig.backendBaseUrl.isNotBlank()

    /** Schalter aus den Einstellungen (Auto-Alarme der App). */
    val isEnabled: Boolean get() = endpointConfig.slackEnabled

    /** Ziel-Channel aus den Einstellungen; leer = Default des Backends. */
    val defaultChannel: String get() = endpointConfig.slackChannel

    private val _health = MutableStateFlow<SlackHealth?>(null)
    val health: StateFlow<SlackHealth?> = _health.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // ============ DATENMODELLE ============

    data class SlackHealth(
        val configured: Boolean = false,
        val reachable: Boolean? = null,
        val transport: String = "",
        val url: String = "",
        val serverName: String = "",
        val serverVersion: String = "",
        val tools: Int = 0,
        val toolNames: List<String> = emptyList(),
        val notifyEnabled: Boolean = false,
        val notifyChannel: String = "",
        val minSeverity: String = "",
        val webhookConfigured: Boolean = false,
        val error: String? = null
    )

    data class SlackTool(
        val name: String,
        val description: String
    )

    data class SlackChannel(
        val id: String,
        val name: String,
        val topic: String = "",
        val memberCount: String = ""
    )

    data class SlackCallResult(
        val ok: Boolean,
        val tool: String,
        val text: String,
        val isError: Boolean,
        val error: String?
    )

    data class SlackNotifyResult(
        val ok: Boolean,
        val channel: String,
        val transport: String,
        val detail: String
    )

    // ============ API ============

    /** Holt den Slack-Status; `probe = false` fragt den MCP-Server nicht live ab. */
    suspend fun fetchHealth(probe: Boolean = true): SlackHealth? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        _isProcessing.value = true
        try {
            val request = Request.Builder()
                .url("${endpointConfig.backendBaseUrl}/api/slack/health?probe=$probe")
                .get()
                .applyAuth()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseHealth(response.body?.string().orEmpty()).also { _health.value = it }
            }
        } catch (_: Exception) {
            null
        } finally {
            _isProcessing.value = false
        }
    }

    /** Registrierte MCP-Tools des Slack-MCP-Servers. */
    suspend fun fetchTools(refresh: Boolean = false): List<SlackTool> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        _isProcessing.value = true
        try {
            val request = Request.Builder()
                .url("${endpointConfig.backendBaseUrl}/api/slack/tools?refresh=$refresh")
                .get()
                .applyAuth()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                json.get("tools")?.asJsonArray?.mapNotNull { el ->
                    val o = el.asJsonObject
                    SlackTool(
                        name = o.str("name"),
                        description = o.str("description")
                    )
                }.orEmpty()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            _isProcessing.value = false
        }
    }

    /** Channel-Verzeichnis (MCP-Tool `channels_list`). */
    suspend fun fetchChannels(limit: Int = 200): List<SlackChannel> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        _isProcessing.value = true
        try {
            val request = Request.Builder()
                .url("${endpointConfig.backendBaseUrl}/api/slack/channels?limit=$limit")
                .get()
                .applyAuth()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                json.get("channels")?.asJsonArray?.mapNotNull { el ->
                    val o = el.asJsonObject
                    SlackChannel(
                        id = o.str("id"),
                        name = o.str("name"),
                        topic = o.str("topic"),
                        memberCount = o.str("memberCount")
                    )
                }.orEmpty()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Direkter MCP-Tool-Aufruf (z. B. `conversations_history`,
     * `conversations_search_messages`, `users_search`).
     */
    suspend fun callTool(
        tool: String,
        arguments: Map<String, Any?> = emptyMap()
    ): SlackCallResult? = withContext(Dispatchers.IO) {
        if (!isConfigured || tool.isBlank()) return@withContext null
        _isProcessing.value = true
        try {
            val payload = gson.toJson(mapOf("tool" to tool, "arguments" to arguments))
            val result = post("/api/slack/call", payload) ?: return@withContext null
            SlackCallResult(
                ok = result.get("ok")?.asBoolean ?: false,
                tool = result.str("tool").ifBlank { tool },
                text = result.str("text"),
                isError = result.get("is_error")?.asBoolean ?: false,
                error = result.get("error")?.takeIf { !it.isJsonNull }?.asString
            )
        } catch (_: Exception) {
            null
        } finally {
            _isProcessing.value = false
        }
    }

    /** Meldung in einen Slack-Channel senden (Channel leer = Backend-Default). */
    suspend fun notify(
        message: String,
        channel: String? = null,
        severity: String = "INFO",
        assetId: String? = null,
        alertType: String = "STATUS"
    ): SlackNotifyResult? = withContext(Dispatchers.IO) {
        if (!isConfigured || message.isBlank()) return@withContext null
        _isProcessing.value = true
        try {
            val body = mutableMapOf<String, Any?>(
                "message" to message,
                "severity" to severity,
                "alert_type" to alertType
            )
            // Priorität: Aufrufer → Einstellungen → Default des Backends.
            val target = channel?.takeIf { it.isNotBlank() } ?: defaultChannel
            if (target.isNotBlank()) body["channel"] = target
            if (!assetId.isNullOrBlank()) body["asset_id"] = assetId
            val result = post("/api/slack/notify", gson.toJson(body)) ?: return@withContext null
            SlackNotifyResult(
                ok = result.get("ok")?.asBoolean ?: false,
                channel = result.str("channel"),
                transport = result.str("transport"),
                detail = result.str("detail")
            )
        } catch (_: Exception) {
            null
        } finally {
            _isProcessing.value = false
        }
    }

    /** Testmeldung an den konfigurierten Alert-Channel (für Setup/Abnahme). */
    suspend fun sendTestMessage(): SlackNotifyResult? =
        notify(
            message = "SecureGuard-App: Slack-Integration funktioniert ✔",
            severity = "INFO",
            alertType = "TEST"
        )

    /** Alert-Meldung (wird serverseitig gegen die Mindest-Schwere geprüft). */
    suspend fun notifyAlert(
        assetId: String,
        alertType: String,
        severity: String,
        message: String
    ): SlackNotifyResult? = notify(
        message = message,
        severity = severity,
        assetId = assetId,
        alertType = alertType
    )

    // ============ ABHÄNGIGKEITEN (Einstellungen) ============

    /** Eine serverseitige Abhängigkeit aus `GET /api/system/dependencies`. */
    data class ServerDependency(
        val id: String,
        val name: String,
        val kind: String,
        val target: String,
        val configured: Boolean,
        val reachable: Boolean?,
        val detail: String
    )

    /**
     * Holt die serverseitige Abhängigkeits-Inventur (DB, MQTT, Slack-MCP,
     * Webhook, Node-RED) – die App zeigt sie zusammen mit den lokalen
     * Endpunkten im Einstellungsmenü.
     */
    suspend fun fetchDependencies(probe: Boolean = true): List<ServerDependency> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext emptyList()
            _isProcessing.value = true
            try {
                val request = Request.Builder()
                    .url(
                        endpointConfig.backendBaseUrl +
                            "/api/system/dependencies?probe=$probe"
                    )
                    .get()
                    .applyAuth()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val json = JsonParser.parseString(response.body?.string().orEmpty())
                        .asJsonObject
                    json.get("dependencies")?.asJsonArray?.mapNotNull { el ->
                        val o = el.asJsonObject
                        ServerDependency(
                            id = o.str("id"),
                            name = o.str("name"),
                            kind = o.str("kind"),
                            target = o.str("target"),
                            configured = o.get("configured")?.asBoolean ?: false,
                            reachable = o.get("reachable")
                                ?.takeIf { !it.isJsonNull }?.asBoolean,
                            detail = o.str("detail")
                        )
                    }.orEmpty()
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                _isProcessing.value = false
            }
        }

    // ============ INTERN ============

    private fun post(path: String, json: String): JsonObject? {
        val request = Request.Builder()
            .url(endpointConfig.backendBaseUrl + path)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .applyAuth()
            .build()
        return http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use null
            // 4xx/5xx tragen das Fehler-JSON im Body – trotzdem parsen.
            runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        }
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val key = endpointConfig.backendApiKey
        if (key.isNotBlank()) header("X-API-Key", key)
        return this
    }

    private fun parseHealth(body: String): SlackHealth {
        val json = runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrNull() ?: return SlackHealth()
        val notify = json.get("notify")?.takeIf { it.isJsonObject }?.asJsonObject
        val server = json.get("server")?.takeIf { it.isJsonObject }?.asJsonObject
        val toolNames = json.get("tool_names")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.map { it.asString }
            .orEmpty()
        return SlackHealth(
            configured = json.get("configured")?.asBoolean ?: false,
            reachable = json.get("reachable")?.takeIf { !it.isJsonNull }?.asBoolean,
            transport = json.str("transport"),
            url = json.str("url"),
            serverName = server?.str("name").orEmpty(),
            serverVersion = server?.str("version").orEmpty(),
            tools = json.get("tools")?.takeIf { !it.isJsonNull }?.asInt ?: toolNames.size,
            toolNames = toolNames,
            notifyEnabled = notify?.get("enabled")?.asBoolean ?: false,
            notifyChannel = notify?.str("channel").orEmpty(),
            minSeverity = notify?.str("min_severity").orEmpty(),
            webhookConfigured = notify?.get("webhook_configured")?.asBoolean ?: false,
            error = json.get("error")?.takeIf { !it.isJsonNull }?.asString
        )
    }

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
