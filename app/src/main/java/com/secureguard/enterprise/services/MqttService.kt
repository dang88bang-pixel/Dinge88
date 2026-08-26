package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MQTT-Client (Paho) für Echtzeit-Kommunikation mit Assets/Gateways.
 *
 * Implementiert mit [MqttAsyncClient] aus `org.eclipse.paho.client.mqttv3`
 * (nicht mit dem veralteten `paho.android.service`). Der Client verbindet sich
 * nur, wenn eine Broker-URL in `local.properties` konfiguriert ist. Er
 * abonniert Telemetrie-, Alert-, Status- und Suchantwort-Themen und emittiert
 * [MqttEvent]s, die vom Agenten verarbeitet werden.
 */
@Singleton
class MqttService @Inject constructor() {

    private val gson = Gson()

    private var client: MqttAsyncClient? = null

    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<MqttEvent> = _events.asSharedFlow()

    val isConnected: Boolean get() = client?.isConnected == true

    /** Verbindet mit dem Broker und abonniert die Standard-Themen. */
    @Synchronized
    fun connect() {
        if (client?.isConnected == true) return
        val brokerUrl = normalizeBrokerUrl(MqttConfig.BROKER_URL)
        if (brokerUrl.isBlank()) {
            _events.tryEmit(MqttEvent.Error("Keine MQTT-Broker-URL konfiguriert (MQTT_BROKER_URL)"))
            return
        }
        val clientId = "${MqttConfig.CLIENT_ID_PREFIX}_${System.currentTimeMillis()}"
        val newClient = try {
            MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
        } catch (e: Exception) {
            _events.tryEmit(MqttEvent.Error("MQTT-Client konnte nicht erstellt werden: ${e.message}"))
            return
        }
        client = newClient

        newClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _events.tryEmit(MqttEvent.Connected(reconnect))
                subscribe(MqttConfig.TOPIC_TELEMETRY)
                subscribe(MqttConfig.TOPIC_ALERT)
                subscribe(MqttConfig.TOPIC_STATUS)
                subscribe(MqttConfig.TOPIC_BROADCAST)
                subscribe(MqttConfig.TOPIC_SEARCH_RESPONSE)
                subscribe(MqttConfig.TOPIC_SEARCH_RESPONSE_ALT)
            }

            override fun connectionLost(cause: Throwable?) {
                _events.tryEmit(MqttEvent.Disconnected(cause?.message))
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                handleMessage(topic, message)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // bestätigt – kein Handlungsbedarf
            }
        })

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            keepAliveInterval = MqttConfig.KEEP_ALIVE_SECONDS
            connectionTimeout = MqttConfig.CONNECT_TIMEOUT_SECONDS
            if (MqttConfig.USERNAME.isNotEmpty()) {
                userName = MqttConfig.USERNAME
                password = MqttConfig.PASSWORD.toCharArray()
            }
        }

        try {
            newClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // Verbindung steht – Subscriptions laufen über connectComplete
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    _events.tryEmit(
                        MqttEvent.Error(
                            "MQTT-Verbindung fehlgeschlagen: ${exception?.message ?: "unbekannt"}"
                        )
                    )
                }
            })
        } catch (e: Exception) {
            _events.tryEmit(MqttEvent.Error("MQTT-connect-Fehler: ${e.message}"))
        }
    }

    /** Normalisiert Broker-Schemata auf Paho-Schemata (`ssl://` für TLS). */
    private fun normalizeBrokerUrl(url: String): String {
        var result = url.trim()
        when {
            result.startsWith("mqtts://") -> result = "ssl://" + result.removePrefix("mqtts://")
            result.startsWith("tls://") -> result = "ssl://" + result.removePrefix("tls://")
            result.startsWith("mqtt://") && MqttConfig.USE_TLS ->
                result = "ssl://" + result.removePrefix("mqtt://")
        }
        return result
    }

    /** Abonniert ein Topic (QoS siehe [MqttConfig]). */
    fun subscribe(topic: String, qos: Int = MqttConfig.QOS_TELEMETRY) {
        val c = client ?: return
        try {
            c.subscribe(topic, qos)
        } catch (e: Exception) {
            _events.tryEmit(MqttEvent.Error("MQTT-Subscribe-Fehler: ${e.message}"))
        }
    }

    /** Veröffentlicht eine Nachricht auf einem Topic. */
    fun publish(topic: String, payload: String, qos: Int = MqttConfig.QOS_COMMAND) {
        val c = client ?: return
        if (!c.isConnected) {
            _events.tryEmit(MqttEvent.Error("MQTT nicht verbunden – Nachricht verworfen: $topic"))
            return
        }
        try {
            c.publish(topic, MqttMessage(payload.toByteArray()).apply { this.qos = qos })
        } catch (e: Exception) {
            _events.tryEmit(MqttEvent.Error("MQTT-Publish-Fehler: ${e.message}"))
        }
    }

    /** Sendet einen Befehl an ein Asset. */
    fun sendCommand(assetMac: String, command: String) {
        publish(MqttConfig.commandTopic(assetMac), command, MqttConfig.QOS_COMMAND)
    }

    /** Sendet eine asynchrone Suchanfrage für ein Asset an alle Gateways/Backend. */
    fun sendSearchRequest(assetMac: String) {
        if (!isConnected) return
        publish(
            MqttConfig.TOPIC_SEARCH_REQUEST,
            "{\"mac\":\"${assetMac.uppercase()}\",\"requestedAt\":${System.currentTimeMillis()}}",
            MqttConfig.QOS_SEARCH
        )
    }

    @Synchronized
    fun disconnect() {
        try {
            client?.disconnect()
        } catch (_: Exception) {
        }
        client = null
        _events.tryEmit(MqttEvent.Disconnected(null))
    }

    // ============ INTERN ============

    private fun handleMessage(topic: String?, message: MqttMessage?) {
        val payload = message?.let { String(it.payload) } ?: return
        val t = topic ?: return
        try {
            when {
                t.contains("/search/response") || t.contains("/search_response") -> {
                    val json = gson.fromJson(payload, JsonObject::class.java)
                    val mac = json.get("mac")?.asString
                        ?: json.get("assetMac")?.asString
                        ?: extractMac(t)
                    _events.tryEmit(
                        MqttEvent.SearchResponse(
                            assetMac = mac ?: t,
                            rssi = json.get("rssi")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                            latitude = json.get("lat")
                                ?.takeIf { !it.isJsonNull }?.asDouble
                                ?: json.get("latitude")?.takeIf { !it.isJsonNull }?.asDouble,
                            longitude = json.get("lng")
                                ?.takeIf { !it.isJsonNull }?.asDouble
                                ?: json.get("longitude")?.takeIf { !it.isJsonNull }?.asDouble,
                            accuracyMeters = json.get("accuracyMeters")
                                ?.takeIf { !it.isJsonNull }?.asFloat
                                ?: json.get("accuracy_meters")?.takeIf { !it.isJsonNull }?.asFloat,
                            isHistorical = json.get("isHistorical")
                                ?.takeIf { !it.isJsonNull }?.asBoolean
                                ?: json.get("historical")?.takeIf { !it.isJsonNull }?.asBoolean
                                ?: false,
                            payload = payload
                        )
                    )
                }

                t.endsWith("/telemetry") -> {
                    val json = gson.fromJson(payload, JsonObject::class.java)
                    val mac = json.get("mac")?.asString ?: extractMac(t)
                    _events.tryEmit(
                        MqttEvent.Telemetry(
                            assetMac = mac ?: t,
                            rssi = json.get("rssi")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                            latitude = json.get("lat")?.takeIf { !it.isJsonNull }?.asDouble,
                            longitude = json.get("lng")?.takeIf { !it.isJsonNull }?.asDouble,
                            payload = payload
                        )
                    )
                }

                t.endsWith("/alert") -> {
                    val json = gson.fromJson(payload, JsonObject::class.java)
                    val mac = json.get("mac")?.asString ?: extractMac(t)
                    _events.tryEmit(
                        MqttEvent.Alert(
                            assetMac = mac ?: t,
                            message = json.get("message")?.takeIf { !it.isJsonNull }?.asString
                                ?: payload
                        )
                    )
                }

                t.endsWith("/status") -> {
                    _events.tryEmit(MqttEvent.Status(t, payload))
                }

                t == MqttConfig.TOPIC_BROADCAST -> {
                    _events.tryEmit(MqttEvent.Broadcast(payload))
                }
            }
        } catch (e: Exception) {
            _events.tryEmit(MqttEvent.Error("MQTT-Nachricht konnte nicht geparst werden: ${e.message}"))
        }
    }

    private fun extractMac(topic: String): String? {
        val parts = topic.split("/")
        return parts.getOrNull(1)?.uppercase()
    }
}

/** Ereignisse, die der MQTT-Client emittiert (werden vom Agenten gesammelt). */
sealed class MqttEvent {
    data class Connected(val reconnected: Boolean = false) : MqttEvent()
    data class Disconnected(val reason: String?) : MqttEvent()
    data class Telemetry(
        val assetMac: String,
        val rssi: Int = 0,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val payload: String = ""
    ) : MqttEvent()

    /** Antwort auf eine zuvor gesendete [MqttService.sendSearchRequest]. */
    data class SearchResponse(
        val assetMac: String,
        val rssi: Int = 0,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val accuracyMeters: Float? = null,
        val isHistorical: Boolean = false,
        val payload: String = ""
    ) : MqttEvent()

    data class Alert(val assetMac: String, val message: String) : MqttEvent()
    data class Status(val topic: String, val payload: String) : MqttEvent()
    data class Broadcast(val payload: String) : MqttEvent()
    data class Error(val message: String) : MqttEvent()
}
