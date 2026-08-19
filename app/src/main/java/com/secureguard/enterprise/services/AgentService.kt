package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.AgentSettings
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der selbstlernende Kern-Agent.
 *
 * - Führt periodische Gesamtsuchen über alle Ortungsquellen durch
 * - Adaptive Intervalle basierend auf der Erfolgsquote
 * - Erfahrungsspeicher (letzte 1000 Ereignisse)
 * - Mustererkennung (zeitlich/räumlich/signalbasiert)
 */
@Singleton
class AgentService @Inject constructor(
    private val repository: SecureGuardRepository,
    private val settingsRepository: SettingsRepository,
    private val loraService: LoraService,
    private val telemetryService: TelemetryService,
    private val wifiService: WifiService,
    private val opticalService: OpticalService,
    private val urbanService: UrbanService,
    private val crowdService: CrowdService,
    private val satelliteService: SatelliteService,
    private val notificationService: NotificationService
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var settings = AgentSettings()
    private val experienceMemory = mutableListOf<Experience>()
    private val sourcePriority = mutableListOf<DetectionSource>()
    private val _agentStatus = MutableSharedFlow<AgentStatus>(extraBufferCapacity = 1)
    val agentStatus = _agentStatus.asSharedFlow()

    fun start(settings: AgentSettings) {
        this.settings = settings
        if (isRunning) return // Idempotent: verhindert doppelte Suchschleifen.
        isRunning = true
        settingsRepository.setAgentStartTime(System.currentTimeMillis())
        scope.launch { runBackgroundLoop() }
        scope.launch { emitStatus() }
    }

    fun stop() {
        isRunning = false
        settingsRepository.setAgentStartTime(0L)
    }

    /**
     * Führt genau einen Suchzyklus über alle Assets aus und kehrt zurück.
     * Wird vom Worker verwendet.
     */
    suspend fun runCycleOnce() {
        try {
            val assets = repository.getWhitelistedAssets().first()
            assets.forEach { asset ->
                if (asset.status != AssetStatus.MAINTENANCE) {
                    val result = comprehensiveSearch(asset)
                    handleSearchResult(asset, result)
                }
            }
            if (settings.learningMode) {
                learnFromExperience()
            }
        } catch (e: Exception) {
            // Zyklus fehlertolerant beenden.
        }
    }

    private suspend fun emitStatus() {
        while (isRunning) {
            val assets = runCatching { repository.getWhitelistedAssets().first() }.getOrDefault(emptyList())
            _agentStatus.tryEmit(AgentStatus(isRunning, assets.size, System.currentTimeMillis()))
            delay(5_000L)
        }
    }

    private suspend fun runBackgroundLoop() {
        while (isRunning) {
            runCycleOnce()
            val interval = calculateAdaptiveInterval()
            delay(interval * 1000L)
        }
    }

    /**
     * Führt eine Gesamtsuche über alle Ortungsquellen durch.
     *
     * Wenn der Lernmodus + dynamische Priorisierung aktiv sind, werden die
     * Quellen in der Reihenfolge ihrer bisherigen Erfolgsquote abgefragt
     * (zuerst die erfolgreichste; erster Treffer gewinnt).
     */
    private suspend fun comprehensiveSearch(asset: Asset): SearchResult {
        val usePriority = settings.learningMode && settings.dynamicPriority && sourcePriority.isNotEmpty()

        // Alle Quellen aktiv; Schalter steuern nur, ob eine Quelle übersprungen wird.
        val available = buildMap<DetectionSource, suspend () -> Detection?> {
            put(DetectionSource.LORA) { loraService.searchAsset(asset) }
            if (settingsRepository.bluetooth.value) {
                put(DetectionSource.BLE) { telemetryService.searchAsset(asset) }
            }
            if (settingsRepository.wifi.value) {
                put(DetectionSource.WIFI) { wifiService.searchAsset(asset) }
            }
            put(DetectionSource.OPTICAL) { opticalService.searchAsset(asset) }
            put(DetectionSource.URBAN) { urbanService.searchAsset(asset) }
            if (asset.externalAllowed && settings.externalSources) {
                put(DetectionSource.CROWD) { crowdService.searchAsset(asset) }
            }
            if (settingsRepository.location.value) {
                put(DetectionSource.SATELLITE) { satelliteService.searchAsset(asset) }
            }
        }

        val order = if (usePriority) {
            sourcePriority.filter { available.containsKey(it) } +
                available.keys.filter { !sourcePriority.contains(it) }
        } else {
            available.keys.toList()
        }

        for (source in order) {
            val provider = available[source] ?: continue
            val detection = runCatching { provider() }.getOrNull()
            if (detection != null) {
                return SearchResult(found = true, detection = detection, accuracy = detection.rssi)
            }
        }
        return SearchResult(found = false)
    }

    private suspend fun handleSearchResult(asset: Asset, result: SearchResult) {
        val now = System.currentTimeMillis()
        val detection = result.detection
        if (result.found && detection != null) {
            repository.updateAssetStatus(
                asset.mac, AssetStatus.ONLINE, now,
                detection.latitude, detection.longitude
            )
            repository.insertDetection(detection)
            notificationService.sendFoundNotification(asset, detection)
            experienceMemory.add(
                Experience(asset.id, true, detection.sourceType, Date())
            )
            trimExperienceMemory()
        } else {
            repository.updateAssetStatus(asset.mac, AssetStatus.OFFLINE, now, null, null)
            // Warnung nur bei längerer Abwesenheit erzeugen.
            if (shouldCreateOfflineAlert(asset)) {
                repository.insertAlert(
                    Alert(
                        assetId = asset.id,
                        type = AlertType.SECURITY,
                        severity = AlertSeverity.WARNING,
                        message = "${asset.shortName} wurde nicht gefunden.",
                        timestamp = Date()
                    )
                )
            }
            experienceMemory.add(
                Experience(asset.id, false, DetectionSource.UNKNOWN, Date())
            )
            trimExperienceMemory()
        }
    }

    private fun shouldCreateOfflineAlert(asset: Asset): Boolean {
        val lastSeen = asset.lastSeen?.time ?: 0L
        val now = System.currentTimeMillis()
        return (now - lastSeen) > OFFLINE_ALERT_THRESHOLD_MS
    }

    private fun trimExperienceMemory() {
        if (experienceMemory.size > EXPERIENCE_MEMORY_SIZE) {
            experienceMemory.removeRange(0, experienceMemory.size - EXPERIENCE_MEMORY_SIZE)
        }
    }

    /**
     * Lernt aus den letzten 100 Ereignissen die Erfolgsquote je Ortungsquelle
     * und sortiert die Kanal-Priorität entsprechend (erfolgreichste zuerst).
     * Unbekannte Quellen (nie geliefert) wandern nach hinten.
     */
    private suspend fun learnFromExperience() {
        val last100 = experienceMemory.takeLast(100)
        if (last100.isEmpty()) return

        // Erfolgsquote pro Quelle (nur gelieferte Quellen zählen).
        val delivered = last100.filter { it.source != DetectionSource.UNKNOWN }
        if (delivered.isEmpty()) return

        val successCount = delivered.groupingBy { it.source }
            .eachCount()
            .mapValues { (_, count) -> count.toDouble() / delivered.size }

        val ranked = successCount.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val unknownOrder = DetectionSource.entries.filter { !successCount.containsKey(it) }
        sourcePriority.clear()
        sourcePriority.addAll(ranked)
        sourcePriority.addAll(unknownOrder)
    }

    /**
     * Berechnet das adaptive Suchintervall basierend auf der Erfolgsquote.
     * Bei hoher Erfolgsquote wird seltener gesucht (Energieersparnis).
     */
    fun calculateAdaptiveInterval(): Long {
        val last100 = experienceMemory.takeLast(100)
        if (last100.isEmpty()) return settings.interval.toLong()
        val successRate = last100.count { it.success }.toDouble() / last100.size
        return when {
            successRate > 0.9 -> MAX_INTERVAL_SECONDS
            successRate > 0.7 -> MID_INTERVAL_SECONDS
            else -> MIN_INTERVAL_SECONDS
        }.coerceAtLeast(settings.interval.toLong())
    }

    companion object {
        const val EXPERIENCE_MEMORY_SIZE = 1000
        const val OFFLINE_ALERT_THRESHOLD_MS = 30 * 60 * 1000L // 30 Minuten
        const val MIN_INTERVAL_SECONDS = 30L
        const val MID_INTERVAL_SECONDS = 60L
        const val MAX_INTERVAL_SECONDS = 120L
    }
}

data class SearchResult(val found: Boolean, val detection: Detection? = null, val accuracy: Int = 0)

data class Experience(
    val assetId: String,
    val success: Boolean,
    val source: DetectionSource,
    val timestamp: Date
)

data class AgentStatus(val running: Boolean, val assetsTracked: Int, val lastRun: Long)
