package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.config.EndpointConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket-Integration (OkHttp) für Echtzeit-Updates von der SecureGuard-
 * Fleet-Instanz. URL kommt aus [EndpointConfig] (Settings zur Laufzeit).
 */
@Singleton
class WebSocketService @Inject constructor(
    private val endpointConfig: EndpointConfig
) {

    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
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

    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    val isConfigured: Boolean get() = endpointConfig.websocketUrl.isNotBlank()

    val currentUrl: String get() = endpointConfig.websocketUrl

    /** Baut die WebSocket-Verbindung auf. */
    fun connect(url: String = endpointConfig.websocketUrl) {
        if (url.isBlank()) {
            _events.tryEmit(WebSocketEvent.Error("Keine WebSocket-URL konfiguriert (WEBSOCKET_URL / Settings)"))
            return
        }
        disconnect()
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _events.tryEmit(WebSocketEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    val data: Map<String, Any> = message.data ?: emptyMap()
                    when (message.type) {
                        "telemetry" -> _events.tryEmit(WebSocketEvent.Telemetry(data))
                        "alert" -> _events.tryEmit(WebSocketEvent.Alert(data))
                        "asset_update" -> _events.tryEmit(WebSocketEvent.AssetUpdate(data))
                        "system_status" -> _events.tryEmit(WebSocketEvent.SystemStatus(data))
                        else -> _events.tryEmit(WebSocketEvent.Unknown(message.type, data))
                    }
                } catch (e: Exception) {
                    _events.tryEmit(WebSocketEvent.Error(e.message ?: "Parsing-Fehler"))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _events.tryEmit(WebSocketEvent.Disconnected(reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.tryEmit(WebSocketEvent.Error(t.message ?: "Verbindung fehlgeschlagen"))
            }
        })
    }

    fun reconnect() {
        disconnect()
        if (isConfigured) connect()
    }

    fun sendMessage(data: Any) {
        try {
            webSocket?.send(gson.toJson(data))
        } catch (e: Exception) {
            _events.tryEmit(WebSocketEvent.Error("Senden fehlgeschlagen: ${e.message}"))
        }
    }

    fun sendCommand(assetId: String, action: String) {
        sendMessage(
            mapOf(
                "type" to "command",
                "assetId" to assetId,
                "action" to action
            )
        )
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {
        }
        webSocket = null
    }
}

sealed class WebSocketEvent {
    object Connected : WebSocketEvent()
    data class Disconnected(val reason: String?) : WebSocketEvent()
    data class Telemetry(val data: Map<String, Any>) : WebSocketEvent()
    data class Alert(val data: Map<String, Any>) : WebSocketEvent()
    data class AssetUpdate(val data: Map<String, Any>) : WebSocketEvent()
    data class SystemStatus(val data: Map<String, Any>) : WebSocketEvent()
    data class Unknown(val type: String?, val data: Map<String, Any>) : WebSocketEvent()
    data class Error(val message: String) : WebSocketEvent()
}

data class WebSocketMessage(
    val type: String? = null,
    val data: Map<String, Any>? = null
)
