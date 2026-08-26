package com.secureguard.enterprise

import com.google.common.truth.Truth.assertThat
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.SearchResult
import com.secureguard.enterprise.services.AgentCycleResult
import com.secureguard.enterprise.services.AgentSettings
import org.junit.Test
import java.util.Date

/**
 * Agent-Cycle-Logik (Fusion „best RSSI wins“, Cycle-Result, Settings-Defaults)
 * ohne volle Service-DI – spiegelt die Orchestrierung in [com.secureguard.enterprise.services.AgentService].
 */
class AgentCycleLogicTest {

    @Test
    fun settings_default_offlineOnly_safe() {
        val s = AgentSettings()
        assertThat(s.offlineOnly).isTrue()
        assertThat(s.interval).isEqualTo(30)
        assertThat(s.learningMode).isTrue()
        assertThat(s.externalSources).isFalse()
    }

    @Test
    fun fuse_picks_strongest_rssi() {
        val weak = Detection(
            assetMac = "AA:BB:CC:DD:EE:01",
            sourceType = DetectionSource.WIFI,
            rssi = -90,
            timestamp = Date()
        )
        val strong = Detection(
            assetMac = "AA:BB:CC:DD:EE:01",
            sourceType = DetectionSource.BLE,
            rssi = -40,
            timestamp = Date()
        )
        val best = listOf(weak, strong).minByOrNull { it.rssi }!!
        val result = SearchResult(found = true, detection = best, accuracy = best.rssi)
        assertThat(result.found).isTrue()
        assertThat(result.detection!!.sourceType).isEqualTo(DetectionSource.BLE)
        assertThat(result.accuracy).isEqualTo(-40)
    }

    @Test
    fun notFound_when_no_channels_hit() {
        assertThat(SearchResult.NotFound.found).isFalse()
        assertThat(SearchResult.NotFound.detection).isNull()
    }

    @Test
    fun cycle_result_aggregates_hits() {
        val channelHits = mutableMapOf<String, Int>()
        val detections = listOf(
            DetectionSource.BLE,
            DetectionSource.BLE,
            DetectionSource.LORA
        )
        var hits = 0
        detections.forEach { src ->
            hits++
            channelHits[src.name] = (channelHits[src.name] ?: 0) + 1
        }
        val result = AgentCycleResult(
            assetsChecked = 5,
            detections = hits,
            channelHits = channelHits
        )
        assertThat(result.assetsChecked).isEqualTo(5)
        assertThat(result.detections).isEqualTo(3)
        assertThat(result.channelHits["BLE"]).isEqualTo(2)
        assertThat(result.channelHits["LORA"]).isEqualTo(1)
    }

    @Test
    fun learning_memory_stores_last_winning_channel() {
        val learningMemory = mutableMapOf<String, DetectionSource>()
        val mac = "aa:bb:cc:dd:ee:01"
        val result = SearchResult(
            found = true,
            detection = Detection(
                assetMac = mac,
                sourceType = DetectionSource.MQTT,
                rssi = -55,
                timestamp = Date()
            ),
            accuracy = -55
        )
        if (result.found && result.detection != null) {
            learningMemory[mac.uppercase()] = result.detection!!.sourceType
        }
        assertThat(learningMemory["AA:BB:CC:DD:EE:01"]).isEqualTo(DetectionSource.MQTT)
    }
}
