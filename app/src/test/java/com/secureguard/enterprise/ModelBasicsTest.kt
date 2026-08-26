package com.secureguard.enterprise

import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBasicsTest {

    @Test
    fun searchResult_notFound_isStable() {
        assertFalse(SearchResult.NotFound.found)
        assertEquals(null, SearchResult.NotFound.detection)
    }

    @Test
    fun enums_cover_all_channels() {
        val names = DetectionSource.entries.map { it.name }.toSet()
        assertTrue(names.containsAll(listOf("BLE", "WIFI", "LORA", "API", "MQTT", "NFC")))
    }

    @Test
    fun assetStatus_has_online_offline() {
        assertEquals(5, AssetStatus.entries.size)
        assertTrue(AssetStatus.entries.any { it == AssetStatus.ONLINE })
        assertTrue(AssetStatus.entries.any { it == AssetStatus.OFFLINE })
    }
}
