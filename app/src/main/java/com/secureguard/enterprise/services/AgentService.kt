package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-learning orchestration agent.
 *
 * Every cycle it queries every available channel for every whitelisted asset,
 * fuses the results (best RSSI wins), persists the winning detection and
 * updates the asset's status. In [AgentSettings.learningMode] the channel that
 * most recently produced a hit for an asset is queried first on the next cycle
 * ("rekursive Verbesserung"). The agent is fully decoupled from Meshtastic:
 * LoRa is just one of several channels via [LoraService].
 */
@Singleton
class AgentService @Inject constructor(
    private val database: SecureGuardDatabase,
    private val loraService: LoraService,
    private val bleService: BleService,
    private val wifiService: WifiService,
    private val telemetryService: TelemetryService,
    private val opticalService: OpticalService,
    private val urbanService: UrbanService,
    private val crowdService: CrowdService,
    private val satelliteService: SatelliteService,
    private val notificationService: NotificationService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    private val _agentStatus = MutableStateFlow(AgentStatus())
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val cycleCount = AtomicLong(0)

    /** Recent channel hits per MAC, used for learning-mode prioritisation. */
    private val learningMemory = mutableMapOf<String, DetectionSource>()

    @Synchronized
    fun start(settings: AgentSettings = AgentSettings()) {
        if (loopJob?.isActive == true) return
        val now = System.currentTimeMillis()
        _agentStatus.value = AgentStatus(
            running = true,
            startedAt = now,
            nextRunAt = now,
            settings = settings
        )
        loopJob = scope.launch { runLoop(settings) }
    }

    @Synchronized
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _agentStatus.value = _agentStatus.value.copy(running = false)
    }

    private suspend fun runLoop(settings: AgentSettings) {
        while (scope.isActive) {
            val cycleStart = System.currentTimeMillis()
            val result = runCatching { runCycle(settings) }
                .getOrDefault(AgentCycleResult())

            val now = System.currentTimeMillis()
            val intervalMs = (settings.interval.coerceAtLeast(5)) * 1000L
            _agentStatus.value = _agentStatus.value.copy(
                lastRunAt = now,
                nextRunAt = now + intervalMs,
                cycle = cycleCount.incrementAndGet(),
                detectionsThisCycle = result.detections
            )

            notificationService.buildAgentNotification(
                "Zyklus ${cycleCount.get()} · ${result.assetsChecked} Assets geprüft · " +
                    "${result.detections} Treffer"
            )

            val elapsed = System.currentTimeMillis() - cycleStart
            delay((intervalMs - elapsed).coerceAtLeast(0L))
        }
    }

    /** Runs one complete cycle over all whitelisted assets. */
    suspend fun runCycle(settings: AgentSettings = _agentStatus.value.settings): AgentCycleResult {
        // Take a snapshot by using the first emission is awkward inside a suspend fun;
        // query via a one-shot list instead.
        val snapshot = currentWhitelistedAssets()
        var hits = 0
        val channelHits = mutableMapOf<String, Int>()

        for (asset in snapshot) {
            val result = comprehensiveSearch(asset, settings)
            if (result.found && result.detection != null) {
                hits++
                val key = result.detection.sourceType.name
                channelHits[key] = (channelHits[key] ?: 0) + 1
                if (settings.learningMode) {
                    learningMemory[asset.mac.uppercase()] = result.detection.sourceType
                }
            }
        }
        return AgentCycleResult(
            assetsChecked = snapshot.size,
            detections = hits,
            channelHits = channelHits
        )
    }

    private suspend fun currentWhitelistedAssets(): List<Asset> {
        // Room's @Query returns a Flow; take the current snapshot.
        return database.assetDao().observeWhitelisted().first()
    }

    /** Queries every channel for [asset] and returns the best detection (lowest RSSI). */
    suspend fun comprehensiveSearch(
        asset: Asset,
        settings: AgentSettings = _agentStatus.value.settings
    ): SearchResult = coroutineScope {
        val channels = buildChannelList(asset, settings)

        val results = channels.map { (source, block) ->
            async {
                runCatching { block() }.getOrNull()?.also { detection ->
                    // Persist every channel hit for the history view.
                    persist(detection)
                    if (settings.learningMode) {
                        learningMemory[asset.mac.uppercase()] = source
                    }
                }
            }
        }.awaitAll()

        val best = results.filterNotNull().minByOrNull { it.rssi }
        if (best != null) {
            applyDetectionToAsset(asset, best)
            SearchResult(found = true, detection = best, accuracy = best.rssi)
        } else {
            markOffline(asset)
            SearchResult.NotFound
        }
    }

    private fun buildChannelList(
        asset: Asset,
        settings: AgentSettings
    ): List<Pair<DetectionSource, suspend () -> Detection?>> {
        val all = linkedMapOf<DetectionSource, suspend () -> Detection?>()

        all[DetectionSource.TELEMETRY] = { telemetryService.searchAsset(asset) }
        all[DetectionSource.BLE] = { bleService.searchAsset(asset) }
        all[DetectionSource.WIFI] = { wifiService.searchAsset(asset) }
        all[DetectionSource.LORA] = { loraService.searchAsset(asset) }
        all[DetectionSource.OPTICAL] = { opticalService.searchAsset(asset) }
        all[DetectionSource.URBAN] = { urbanService.searchAsset(asset) }

        // External / internet channels are only used when permitted.
        if (!settings.offlineOnly || settings.externalSources) {
            if (asset.externalAllowed || settings.externalSources) {
                all[DetectionSource.CROWD] = { crowdService.searchAsset(asset) }
            }
            all[DetectionSource.SATELLITE] = { satelliteService.searchAsset(asset) }
        }

        if (!settings.learningMode) return all.toList()

        // Re-order: the channel that last produced a hit goes first.
        val last = learningMemory[asset.mac.uppercase()] ?: return all.toList()
        val ordered = linkedMapOf<DetectionSource, suspend () -> Detection?>()
        all[last]?.let { ordered[last] = it }
        all.forEach { (source, block) -> if (source != last) ordered[source] = block }
        return ordered.toList()
    }

    private suspend fun persist(detection: Detection) {
        database.detectionDao().insert(detection)
    }

    private suspend fun applyDetectionToAsset(asset: Asset, detection: Detection) {
        database.assetDao().updateStatus(
            mac = asset.mac,
            status = AssetStatus.ONLINE,
            rssi = detection.rssi,
            lat = detection.latitude ?: asset.latitude,
            lon = detection.longitude ?: asset.longitude,
            timestamp = detection.timestamp
        )
    }

    private suspend fun markOffline(asset: Asset) {
        val latest = database.detectionDao().latestForAsset(asset.mac)
        val cutoff = System.currentTimeMillis() - STALE_MS
        val isStale = latest == null || latest.timestamp.time < cutoff
        if (isStale && asset.status != AssetStatus.MAINTENANCE) {
            database.assetDao().setStatus(asset.mac, AssetStatus.OFFLINE, Date())
        }
    }

    /** Manually trigger a single-asset search (used by the detail screen). */
    suspend fun searchAsset(asset: Asset): SearchResult = comprehensiveSearch(asset)

    companion object {
        private const val STALE_MS = 5 * 60 * 1000L // 5 minutes
    }
}
