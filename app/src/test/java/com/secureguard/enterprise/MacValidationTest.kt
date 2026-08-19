package com.secureguard.enterprise

import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MacValidationTest {

    private val macRegex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    @Test
    fun acceptsWellFormedMacAddresses() {
        listOf(
            "AA:BB:CC:DD:EE:FF",
            "aa:bb:cc:dd:ee:ff",
            "01:23:45:67:89:ab"
        ).forEach { assertTrue(it, macRegex.matches(it)) }
    }

    @Test
    fun rejectsMalformedMacAddresses() {
        listOf(
            "",
            "AA:BB:CC:DD:EE",
            "AA:BB:CC:DD:EE:FF:00",
            "ZZ:BB:CC:DD:EE:FF",
            "AABBCCDDEEFF"
        ).forEach { assertFalse(it, macRegex.matches(it)) }
    }

    @Test
    fun searchResultPicksBestRssi() {
        val d1 = Detection(
            assetMac = "m", sourceType = DetectionSource.BLE,
            rssi = -80, timestamp = Date()
        )
        val d2 = Detection(
            assetMac = "m", sourceType = DetectionSource.LORA,
            rssi = -45, timestamp = Date()
        )
        val best = listOf(d1, d2).minByOrNull { it.rssi }!!
        val result = SearchResult(found = true, detection = best, accuracy = best.rssi)
        assertEquals(DetectionSource.LORA, result.detection!!.sourceType)
        assertEquals(-45, result.accuracy)
    }

    @Test
    fun notFoundHasNoDetection() {
        val result = SearchResult.NotFound
        assertFalse(result.found)
        assertNull(result.detection)
    }

    @Test
    fun assetStatusValuesAreStable() {
        // Guard against accidental enum reordering that could break persisted ordinal strings.
        assertEquals("ONLINE", AssetStatus.ONLINE.name)
        assertEquals("OFFLINE", AssetStatus.OFFLINE.name)
        assertEquals("MAINTENANCE", AssetStatus.MAINTENANCE.name)
        assertEquals(5, AssetStatus.entries.size)
    }
}
