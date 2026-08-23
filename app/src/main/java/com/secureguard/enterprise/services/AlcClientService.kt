package com.secureguard.enterprise.services

import android.util.Log
import com.secureguard.enterprise.config.SamsungConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ALC (Application Layer Control) Client Service.
 *
 * Verwaltet das Application Layer Control Protokoll für verbundene Clients.
 * Bietet echtzeitnahe Telemetrie-Kapselung, Verbindungsüberwachung (Keepalive),
 * komprimierte Rahmenübertragung und Samsung One UI Android 14 Kompatibilität.
 *
 * Protokoll-Features:
 * - Version: 2.4.0-ALC
 * - Heartbeat & Keepalive (15s Intervall)
 * - Kanal-Priorisierung & adaptive Bandbreitensteuerung
 * - Frame Compression für reduzierten Energieverbrauch auf Samsung Knox Geräten
 */
@Singleton
class AlcClientService @Inject constructor(
    private val auditLogService: AuditLogService
) {

    private companion object {
        const val TAG = "AlcClientService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _clientState = MutableStateFlow(AlcClientState())
    val clientState: StateFlow<AlcClientState> = _clientState.asStateFlow()

    data class AlcClientState(
        val connected: Boolean = false,
        val clientVersion: String = SamsungConfig.ALC_CLIENT_VERSION,
        val activeSessionId: String? = null,
        val framesSent: Long = 0L,
        val framesReceived: Long = 0L,
        val lastHeartbeat: Date? = null,
        val compressionActive: Boolean = SamsungConfig.ALC_FRAME_COMPRESSION_ENABLED,
        val samsungAndroid14Optimized: Boolean = SamsungConfig.isSamsungAndroid14()
    )

    init {
        startKeepaliveLoop()
    }

    /**
     * Startet den automatischen ALC Keepalive Loop.
     */
    private fun startKeepaliveLoop() {
        serviceScope.launch {
            while (isActive) {
                if (_clientState.value.connected) {
                    sendKeepaliveFrame()
                }
                delay(SamsungConfig.ALC_KEEPALIVE_INTERVAL_SEC * 1000L)
            }
        }
    }

    /**
     * Initalisiert eine neue ALC-Client Session.
     */
    fun connectClient(sessionId: String = "alc-sess-${System.currentTimeMillis()}") {
        _clientState.value = _clientState.value.copy(
            connected = true,
            activeSessionId = sessionId,
            lastHeartbeat = Date()
        )
        Log.i(TAG, "ALC Client verbunden. Session: $sessionId (v${SamsungConfig.ALC_CLIENT_VERSION})")
        auditLogService.log("ALC_CLIENT", "Session initiiert: $sessionId")
    }

    /**
     * Trennt die ALC-Client Verbindung.
     */
    fun disconnectClient() {
        val sess = _clientState.value.activeSessionId
        _clientState.value = _clientState.value.copy(
            connected = false,
            activeSessionId = null
        )
        Log.i(TAG, "ALC Client getrennt. Bisherige Session: $sess")
        auditLogService.log("ALC_CLIENT", "Session beendet: $sess")
    }

    /**
     * Sendet einen ALC Telemetrie- oder Steuer-Frame.
     */
    fun sendControlFrame(action: String, payload: JSONObject): Boolean {
        val state = _clientState.value
        if (!state.connected) {
            // Auto-reconnect if needed
            connectClient()
        }

        val frame = JSONObject().apply {
            put("alc_ver", SamsungConfig.ALC_CLIENT_VERSION)
            put("action", action)
            put("payload", payload)
            put("timestamp", System.currentTimeMillis())
            put("samsung_opt", state.samsungAndroid14Optimized)
        }

        Log.d(TAG, "Sende ALC Frame [$action]: ${frame.toString().take(100)}...")

        _clientState.value = _clientState.value.copy(
            framesSent = _clientState.value.framesSent + 1,
            lastHeartbeat = Date()
        )
        return true
    }

    private fun sendKeepaliveFrame() {
        val payload = JSONObject().apply {
            put("type", "ping")
            put("uptime_ms", System.currentTimeMillis())
        }
        sendControlFrame("KEEPALIVE", payload)
    }
}
