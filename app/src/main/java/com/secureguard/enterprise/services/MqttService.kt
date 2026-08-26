package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.secureguard.enterprise.config.EndpointConfig
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
 * Broker-URL und optionale Credentials kommen aus [EndpointConfig]
 * (Settings zur Laufzeit überschreibbar). Unterstützt User/Pass für
 * abgesicherte Broker (Mosquitto allow_anonymous false).
 */
@Singleton
class MqttService @Inject constructor(
    private val endpointConfig: EndpointConfig
) {

    private val gson = Gson()

    private var client: MqttAsyncClient? = null

    private val _events = MutableSharedFlow<MqttEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<MqttEvent> = _events.asSharedFlow()

    val isConnected: Boolean get() = client?.isConnected == true

    val currentBrokerUrl: String get() = endpointConfig.mqttBrokerUrl

    /** Verbindet mit dem Broker und abonniert die Standard-Themen. */
    @Synchronized
    fun connect() {
        if (client?.isConnected == true) return
        val broker = endpointConfig.mqttBrokerUrl
        if (broker.isBlank()) {
            _events.tryEmit(MqttEvent.Error("Keine MQTT-Broker-URL konfiguriert"))
            return
        }
        val clientId = "${MqttConfig.CLIENT_ID_PREFIX}_${System.currentTimeMillis()}"
        val newClient = try {
            MqttAsyncClient(broker, clientId, MemoryPersistence())
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

        val user = endpointConfig.mqttUsername
        val pass = endpointConfig.mqttPassword
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            keepAliveInterval = MqttConfig.KEEP_ALIVE_SECONDS
            connectionTimeout = MqttConfig.CONNECT_TIMEOUT_SECONDS
            if (user.isNotBlank()) {
                userName = user
                if (pass.isNotEmpty()) {
                    password = pass.toCharArray()
                }
            }
            // ssl:// Broker → Default JVM Truststore (System-CAs)
            if (broker.startsWith("ssl://", ignoreCase = true)) {
                socketFactory = javax.net.ssl.SSLSocketFactory.getDefault()
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

    /** Trennt und verbindet neu (nach Settings-Änderung). */
    @Synchronized
    fun reconnect() {
        disconnect()
        connect()
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

    @Synchronized
    fun disconnect() {
        try {
            client?.disconnect()
        } catch (_: Exception) {
        }
        try {
            client?.close()
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

    data class Alert(val assetMac: String, val message: String) : MqttEvent()
    data class Status(val topic: String, val payload: String) : MqttEvent()
    data class Broadcast(val payload: String) : MqttEvent()
    data class Error(val message: String) : MqttEvent()
}
