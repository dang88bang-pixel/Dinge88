package com.secureguard.enterprise.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit-Tests für die reine Port-Ableitungslogik der Automatic Port View. */
class AutomaticPortViewEndpointsTest {

    // ---------- parseHostPort ----------

    @Test
    fun `parst tcp-URL mit Port`() {
        val hp = PortViewEndpoints.parseHostPort("tcp://10.0.2.2:1883", 1883)
        assertEquals("10.0.2.2", hp?.host)
        assertEquals(1883, hp?.port)
    }

    @Test
    fun `parst ws-URL mit Pfad und Port`() {
        val hp = PortViewEndpoints.parseHostPort("ws://secureguard.local:9001/ws", 80)
        assertEquals("secureguard.local", hp?.host)
        assertEquals(9001, hp?.port)
    }

    @Test
    fun `nutzt Default-Port wenn URL keinen Port enthaelt`() {
        val hp = PortViewEndpoints.parseHostPort("https://api.example.com", 443)
        assertEquals("api.example.com", hp?.host)
        assertEquals(443, hp?.port)
    }

    @Test
    fun `parst nackte host-port Angabe`() {
        val hp = PortViewEndpoints.parseHostPort("192.168.1.50:1880", 1883)
        assertEquals("192.168.1.50", hp?.host)
        assertEquals(1880, hp?.port)
    }

    @Test
    fun `nutzt Default-Port bei nacktem Host`() {
        val hp = PortViewEndpoints.parseHostPort("192.168.1.50", 1883)
        assertEquals("192.168.1.50", hp?.host)
        assertEquals(1883, hp?.port)
    }

    @Test
    fun `leere und ungueltige Eingaben ergeben null`() {
        assertNull(PortViewEndpoints.parseHostPort("", 1883))
        assertNull(PortViewEndpoints.parseHostPort("   ", 1883))
        assertNull(PortViewEndpoints.parseHostPort("nicht eine url:::", 1883))
        assertNull(PortViewEndpoints.parseHostPort("://ohne-schema", 1883))
    }

    @Test
    fun `ignoriert ungueltige explizite Ports`() {
        val hp = PortViewEndpoints.parseHostPort("tcp://10.0.2.2:0", 1883)
        assertEquals(1883, hp?.port)
    }

    // ---------- canonicalStackTargets ----------

    @Test
    fun `ergaenzt fehlende Stack-Ports auf primaerem Host`() {
        val taken = listOf(PortViewEndpoints.HostPort("10.0.2.2", 1883))
        val targets = PortViewEndpoints.canonicalStackTargets("10.0.2.2", taken)

        val ids = targets.map { it.id }
        assertEquals(setOf("stack-9001", "stack-8000", "stack-1880"), ids.toSet())
        assertTrue(targets.all { it.host == "10.0.2.2" })
        // Kein Duplikat des bereits konfigurierten MQTT-Ports.
        assertTrue(targets.none { it.port == 1883 })
    }

    @Test
    fun `keine Stack-Ports ohne primaeren Host`() {
        assertTrue(PortViewEndpoints.canonicalStackTargets("", emptyList()).isEmpty())
        assertTrue(PortViewEndpoints.canonicalStackTargets("   ", emptyList()).isEmpty())
    }

    @Test
    fun `andere Hosts werden nicht als belegt gewertet`() {
        val taken = listOf(PortViewEndpoints.HostPort("192.168.1.50", 8000))
        val targets = PortViewEndpoints.canonicalStackTargets("10.0.2.2", taken)
        assertTrue(targets.any { it.id == "stack-8000" })
        assertEquals(4, targets.size)
    }
}
