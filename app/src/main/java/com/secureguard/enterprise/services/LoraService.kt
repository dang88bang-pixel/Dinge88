package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Generic LoRa / LoRaWAN service — replaces the former Meshtastic dependency.
 *
 * No vendor-specific LoRa library is bundled. This class defines the contract
 * ([searchAsset], [sendCommand], [gateways]) and ships with a [DummyLoraClient]
 * so the app is fully functional out of the box. A real backend (Helium,
 * The Things Network, a private gateway fleet, ...) can be plugged in by
 * replacing [loraClient] without touching any other layer.
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val loraClient: LoraClient = DummyLoraClient()

    /** Last known gateways (simulated for the placeholder client). */
    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    suspend fun searchAsset(asset: Asset): Detection? {
        val currentGateways = loraClient.getGateways()
        gateways = currentGateways
        for (gw in currentGateways) {
            if (gw.seenMacs.any { it.equals(asset.mac, ignoreCase = true) }) {
                val detection = Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.LORA,
                    nodeId = gw.id,
                    rssi = gw.rssi,
                    latitude = gw.latitude,
                    longitude = gw.longitude,
                    accuracyMeters = 25f,
                    timestamp = Date()
                )
                emit(detection)
                return detection
            }
        }
        return null
    }

    /**
     * Sends a command to an asset reachable via LoRa. The placeholder client
     * simulates a successful delivery when at least one gateway is in range.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        val inRange = loraClient.getGateways().any { gw ->
            gw.seenMacs.any { it.equals(mac, ignoreCase = true) }
        }
        return inRange || Random.nextFloat() > 0.5f
    }

    suspend fun refreshGateways(): List<Gateway> {
        gateways = loraClient.getGateways()
        return gateways
    }
}

/** A LoRa gateway that recently reported sightings. */
data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val seenMacs: List<String>
)

/** Contract for a pluggable LoRa / LoRaWAN backend. */
interface LoraClient {
    suspend fun getGateways(): List<Gateway>
}

/**
 * Placeholder client used until a real LoRaWAN network server is integrated.
 * It fabricates a handful of gateways around Berlin so the UI and the agent
 * have data to work with.
 */
internal class DummyLoraClient : LoraClient {

    private val pool = listOf(
        Gateway("gw-berlin-mitte", -48, 52.5200, 13.4050, listOf("AA:BB:CC:DD:EE:01")),
        Gateway("gw-kreuzberg", -62, 52.4980, 13.4040, listOf("AA:BB:CC:DD:EE:02")),
        Gateway("gw-prenzlauer-berg", -75, 52.5380, 13.4200, listOf("AA:BB:CC:DD:EE:03")),
        Gateway("gw-alexanderplatz", -55, 52.5219, 13.4132, listOf("AA:BB:CC:DD:EE:04"))
    )

    override suspend fun getGateways(): List<Gateway> {
        // Simulate network jitter.
        return pool.filter { Random.nextFloat() > 0.25f }
            .map { it.copy(rssi = it.rssi + Random.nextInt(-6, 6)) }
    }
}
