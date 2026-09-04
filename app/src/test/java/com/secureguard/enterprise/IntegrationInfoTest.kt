package com.secureguard.enterprise

import com.secureguard.enterprise.config.IntegrationInfo
import com.secureguard.enterprise.config.IntegrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Logik der Abhängigkeitsliste (Einstellungen → „🧩 Anbindungen &
 * Abhängigkeiten"). Keine Android-Abhängigkeiten → läuft als JVM-Unit-Test:
 *
 *     ./gradlew :app:testDebugUnitTest --tests "*IntegrationInfoTest*"
 */
class IntegrationInfoTest {

    @Test
    fun probeState_mapsServerAnswer() {
        assertEquals(
            IntegrationState.CONNECTED,
            IntegrationInfo.stateFromProbe(configured = true, reachable = true)
        )
        assertEquals(
            IntegrationState.MISSING,
            IntegrationInfo.stateFromProbe(configured = true, reachable = false)
        )
        // reachable = null → Server hat nicht geprüft (z. B. Webhook-Fallback)
        assertEquals(
            IntegrationState.CONFIGURED,
            IntegrationInfo.stateFromProbe(configured = true, reachable = null)
        )
        assertEquals(
            IntegrationState.DISABLED,
            IntegrationInfo.stateFromProbe(configured = false, reachable = true)
        )
        assertEquals(
            IntegrationState.DISABLED,
            IntegrationInfo.stateFromProbe(configured = false, reachable = null)
        )
    }

    @Test
    fun secrets_areNeverDisplayed() {
        assertEquals("gesetzt", IntegrationInfo.secretState("xoxb-geheim"))
        assertEquals("nicht gesetzt", IntegrationInfo.secretState(""))
        assertEquals("nicht gesetzt", IntegrationInfo.secretState(null))
    }

    @Test
    fun everyState_hasIconAndLabel() {
        IntegrationState.entries.forEach { state ->
            val info = IntegrationInfo(
                id = "x",
                name = "X",
                kind = "http",
                target = "http://x",
                state = state,
                source = "test"
            )
            assertTrue(info.icon.isNotBlank())   // Status-Icon, nie leer
            assertTrue(info.stateLabel.isNotBlank())
        }
    }

    @Test
    fun stateLabels_areGermanAndDistinct() {
        val labels = IntegrationState.entries.map { state ->
            IntegrationInfo("x", "X", "http", "t", state, "s").stateLabel
        }
        // Jeder Zustand braucht eine eigene, deutsche Beschriftung
        assertEquals(IntegrationState.entries.size, labels.toSet().size)
        assertEquals(
            "verbunden",
            IntegrationInfo("x", "X", "http", "t", IntegrationState.CONNECTED, "s").stateLabel
        )
        assertEquals(
            "nicht erreichbar",
            IntegrationInfo("x", "X", "http", "t", IntegrationState.MISSING, "s").stateLabel
        )
    }
}
