package com.secureguard.enterprise.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Unit-Tests für die [LearningEngine] (Mustererkennung, Prädiktion,
 * Intervall-Optimierung) – reines Kotlin, keine Android-Abhängigkeiten.
 */
class LearningEngineTest {

    private fun exp(
        assetId: String = "asset-1",
        success: Boolean,
        rssi: Int = -60,
        latitude: Double? = null,
        longitude: Double? = null
    ) = Experience(
        assetId = assetId,
        success = success,
        rssi = rssi,
        latitude = latitude,
        longitude = longitude,
        timestamp = Date()
    )

    @Test
    fun `hohe Erfolgsquote ergibt langes Intervall und keine externen Quellen`() {
        val engine = LearningEngine()
        repeat(10) { engine.learn(exp(success = true)) }

        assertEquals(1.0f, engine.confidence.value, 0.001f)
        assertEquals(120, engine.getOptimalInterval())
        assertFalse(engine.shouldUseExternalSources())
    }

    @Test
    fun `niedrige Erfolgsquote ergibt kurzes Intervall und externe Quellen`() {
        val engine = LearningEngine()
        repeat(10) { engine.learn(exp(success = false)) }

        assertEquals(0.0f, engine.confidence.value, 0.001f)
        assertEquals(30, engine.getOptimalInterval())
        assertTrue(engine.shouldUseExternalSources())
    }

    @Test
    fun `Signal-Muster wird aus erfolgreichen Treffern erkannt`() {
        val engine = LearningEngine()
        val patterns = engine.analyzePatterns(
            listOf(
                exp(success = true, rssi = -50),
                exp(success = true, rssi = -60),
                exp(success = true, rssi = -70)
            )
        )

        val signal = patterns.filter { it.type == PatternType.SIGNAL }
        assertEquals(1, signal.size)
        assertEquals(-60.0, signal.first().weight, 0.001)
        assertEquals(10.0, signal.first().metadata["stdDev"]!!, 0.001)
    }

    @Test
    fun `Räumliches Muster ermöglicht Ortsvorhersage`() {
        val engine = LearningEngine()
        val experiences = listOf(
            exp(success = true, latitude = 52.5205, longitude = 13.4051),
            exp(success = true, latitude = 52.5201, longitude = 13.4049),
            exp(success = true, latitude = 52.5199, longitude = 13.4052),
            exp(success = false)
        )

        val patterns = engine.analyzePatterns(experiences)
        val spatial = patterns.filter { it.type == PatternType.SPATIAL }
        assertEquals(1, spatial.size)
        assertEquals("52.52_13.405", spatial.first().key)

        val predicted = engine.predictNextLocation()
        assertNotNull(predicted)
        assertEquals(52.52, predicted!!.first, 0.001)
        assertEquals(13.405, predicted.second, 0.001)
    }

    @Test
    fun `Vorhersage liefert null ohne ausreichende Muster`() {
        val engine = LearningEngine()
        assertNull(engine.predictNextLocation())

        // Einzelner Treffer: zu wenig für Temporal- (≥5) oder Spatial-Muster (≥3)
        val patterns = engine.analyzePatterns(
            listOf(exp(success = true, latitude = 52.5, longitude = 13.4))
        )
        assertTrue(patterns.none { it.type == PatternType.TEMPORAL || it.type == PatternType.SPATIAL })
    }

    @Test
    fun `Erfolgswahrscheinlichkeit gilt je Asset mit Fallback für Unbekannte`() {
        val engine = LearningEngine()
        repeat(3) { engine.learn(exp(success = true)) }
        engine.learn(exp(success = false))

        assertEquals(0.75f, engine.getSuccessProbability("asset-1"), 0.001f)
        assertEquals(0.3f, engine.getSuccessProbability("unbekanntes-asset"), 0.001f)
    }
}
