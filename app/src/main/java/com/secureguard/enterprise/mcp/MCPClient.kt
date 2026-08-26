package com.secureguard.enterprise.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.services.ServiceEndpoints
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * MCP-Client (Model Context Protocol) für temporäre E-Mail-Dienste.
 *
 * Stellt Tools wie `create_inbox`, `wait_for_otp` und `extract_magic_link`
 * über eine WebSocket-Verbindung zu einem MCP-Server bereit. Der Endpunkt
 * wird über `MCP_SERVER_URL` (gradle.properties / local.properties)
 * konfiguriert; ohne Konfiguration geben alle Aufrufe `null` zurück –
 * die App bleibt damit stabil.
 *
 * Verwendungszweck (nur legitime Fälle):
 * - Registrierung für firmeninterne Testumgebungen
 * - API-Key-Generierung für autorisierte Dienste
 * - QA/E2E-Testkonten
 */
@Singleton
class MCPClient @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {

    companion object {
        private const val TIMEOUT_MS = 45_000L // 45 Sekunden
        private val gson = Gson()
    }

    private val serverUrl: String
        get() = ServiceEndpoints.mcpUrl(context)

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    private var webSocket: WebSocket? = null
    private var requestId = 0

    private val pendingRequests = ConcurrentHashMap<Int, (JsonObject) -> Unit>()

    private val _events = MutableSharedFlow<MCPEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MCPEvent> = _events.asSharedFlow()

    // ============ VERBINDUNG ============

    /** Verbindet mit dem MCP-Server (WebSocket). */
    fun connect() {
        if (!isConfigured || webSocket != null) return
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _events.tryEmit(MCPEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    val id = json.get("id")?.asInt ?: return
                    pendingRequests.remove(id)?.invoke(json)
                } catch (e: Exception) {
                    _events.tryEmit(MCPEvent.Error(e.message ?: "Parsing error"))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _events.tryEmit(MCPEvent.Disconnected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.tryEmit(MCPEvent.Error(t.message ?: "Connection failed"))
            }
        })
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    // ============ TOOLS ============

    /** Erstellt eine neue temporäre Inbox (create_inbox). */
    suspend fun createInbox(): InboxResult? {
        if (!isConfigured) return null
        connect()
        val id = ++requestId
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", "create_inbox")
                add("arguments", JsonObject())
            })
        }

        return sendRequest(request, id) { response ->
            val text = extractToolText(response) ?: return@sendRequest null
            val data = gson.fromJson(text, InboxData::class.java)
            InboxResult(
                success = true,
                email = data.email,
                token = data.token,
                inboxId = data.inboxId.ifBlank { UUID.randomUUID().toString() }
            )
        }
    }

    /** Wartet auf eine OTP-E-Mail (wait_for_otp, Long-Polling). */
    suspend fun waitForOTP(inboxToken: String, timeoutMs: Long = TIMEOUT_MS): OTPResult? {
        if (!isConfigured) return null
        connect()
        val id = ++requestId
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", "wait_for_otp")
                add("arguments", JsonObject().apply {
                    addProperty("token", inboxToken)
                    addProperty("timeout", timeoutMs)
                })
            })
        }

        return sendRequest(request, id) { response ->
            val text = extractToolText(response) ?: return@sendRequest null
            val data = gson.fromJson(text, OTPData::class.java)
            if (!data.otp.isNullOrBlank()) {
                OTPResult(
                    success = true,
                    otp = data.otp,
                    email = data.email.orEmpty(),
                    from = data.from.orEmpty(),
                    subject = data.subject.orEmpty()
                )
            } else {
                OTPResult(success = false, error = "Timeout oder keine OTP gefunden")
            }
        }
    }

    /** Extrahiert einen Magic Link aus einer eingegangenen E-Mail. */
    suspend fun extractMagicLink(inboxToken: String): MagicLinkResult? {
        if (!isConfigured) return null
        connect()
        val id = ++requestId
        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", "tools/call")
            add("params", JsonObject().apply {
                addProperty("name", "extract_magic_link")
                add("arguments", JsonObject().apply {
                    addProperty("token", inboxToken)
                })
            })
        }

        return sendRequest(request, id) { response ->
            val text = extractToolText(response) ?: return@sendRequest null
            val data = gson.fromJson(text, MagicLinkData::class.java)
            if (!data.magicLink.isNullOrBlank()) {
                MagicLinkResult(success = true, magicLink = data.magicLink, email = data.email.orEmpty())
            } else {
                MagicLinkResult(success = false, error = "Kein Magic Link gefunden")
            }
        }
    }

    // ============ HILFSFUNKTIONEN ============

    /** Extrahiert das `content[0].text`-Feld einer MCP-Tool-Antwort. */
    private fun extractToolText(response: JsonObject): String? {
        return try {
            val result = response.getAsJsonObject("result")
            val content = result.getAsJsonArray("content")
            content.firstOrNull()?.asJsonObject?.get("text")?.asString
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun <T> sendRequest(
        request: JsonObject,
        id: Int,
        onResponse: (JsonObject) -> T?
    ): T? = suspendCancellableCoroutine { continuation ->
        pendingRequests[id] = { response ->
            try {
                val result = onResponse(response)
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }

        val socket = webSocket
        if (socket == null) {
            pendingRequests.remove(id)
            continuation.resume(null)
        } else {
            try {
                socket.send(request.toString())
            } catch (e: Exception) {
                pendingRequests.remove(id)
                continuation.resume(null)
            }
        }

        continuation.invokeOnCancellation {
            pendingRequests.remove(id)
        }
    }

    // ============ DATENKLASSEN ============

    data class InboxData(
        val email: String = "",
        val token: String = "",
        val inboxId: String = ""
    )

    data class OTPData(
        val otp: String? = null,
        val email: String? = null,
        val from: String? = null,
        val subject: String? = null
    )

    data class MagicLinkData(
        val magicLink: String? = null,
        val email: String? = null
    )
}

/** Ereignisse der MCP-Verbindung. */
sealed class MCPEvent {
    object Connected : MCPEvent()
    object Disconnected : MCPEvent()
    data class Error(val message: String) : MCPEvent()
    data class InboxCreated(val email: String, val token: String) : MCPEvent()
    data class OTPReceived(val otp: String, val email: String) : MCPEvent()
}

/** Ergebnis eines create_inbox-Aufrufs. */
data class InboxResult(
    val success: Boolean,
    val email: String = "",
    val token: String = "",
    val inboxId: String = "",
    val error: String? = null
)

/** Ergebnis eines wait_for_otp-Aufrufs. */
data class OTPResult(
    val success: Boolean,
    val otp: String = "",
    val email: String = "",
    val from: String = "",
    val subject: String = "",
    val error: String? = null
)

/** Ergebnis eines extract_magic_link-Aufrufs. */
data class MagicLinkResult(
    val success: Boolean,
    val magicLink: String = "",
    val email: String = "",
    val error: String? = null
)
