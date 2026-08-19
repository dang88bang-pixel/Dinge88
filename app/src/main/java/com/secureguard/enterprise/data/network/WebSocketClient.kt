package com.secureguard.enterprise.data.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimaler WebSocket-Client (z. B. für Live-Telemetrie von Gateways).
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val client: OkHttpClient
) {
    fun connect(url: String): Flow<String> = callbackFlow {
        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }
        val webSocket = client.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }
}
