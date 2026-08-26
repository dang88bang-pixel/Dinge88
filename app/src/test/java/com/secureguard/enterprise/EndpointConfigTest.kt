package com.secureguard.enterprise

import com.secureguard.enterprise.config.EndpointConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointConfigTest {

    @Test
    fun normalizeMqtt_mqttScheme_toTcp() {
        assertEquals("tcp://192.168.1.10:1883", EndpointConfig.normalizeMqtt("mqtt://192.168.1.10:1883"))
        assertEquals("ssl://broker.example:8883", EndpointConfig.normalizeMqtt("mqtts://broker.example:8883"))
        assertEquals("tcp://10.0.2.2:1883", EndpointConfig.normalizeMqtt("tcp://10.0.2.2:1883"))
    }

    @Test
    fun deriveHttpBase_fromWebsocket() {
        assertEquals(
            "http://192.168.1.10:8000",
            EndpointConfig.deriveHttpBase("ws://192.168.1.10:8000/ws")
        )
        assertEquals(
            "https://api.example.com",
            EndpointConfig.deriveHttpBase("wss://api.example.com/ws")
        )
    }
}
