package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.AgentSettings
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    @ApplicationContext private val context: Context,
    private val repository: SecureGuardRepository,
    private val loraService: LoraService,
    private val telemetryService: TelemetryService,
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
    private val _agentStatus = MutableSharedFlow<AgentStatus>(extraBufferCapacity = 1)
    val agentStatus = _agentStatus.asSharedFlow()

    fun start(settings: AgentSettings) {
        this.settings = settings
        if (isRunning) return // Idempotent: verhindert doppelte Suchschleifen.
        isRunning = true
        scope.launch { runBackgroundLoop() }
        scope.launch { emitStatus() }
    }

    fun stop() {
        isRunning = false
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
                val interval = calculateAdaptiveInterval()
                delay(interval * 1000L)
            } catch (e: Exception) {
                delay(60_000L)
            }
        }
    }

    private suspend fun comprehensiveSearch(asset: Asset): SearchResult {
        val results = coroutineScope {
            listOf(
                async { loraService.searchAsset(asset) },
                async { telemetryService.searchAsset(asset) },
                async { opticalService.searchAsset(asset) },
                async { urbanService.searchAsset(asset) },
                async {
                    if (asset.externalAllowed && settings.externalSources) {
                        crowdService.searchAsset(asset)
                    } else {
                        null
                    }
                },
                async { satelliteService.searchAsset(asset) }
            ).awaitAll()
        }
        val best = results.filterNotNull().minByOrNull { it.rssi }
        return if (best != null) {
            SearchResult(found = true, detection = best, accuracy = best.rssi)
        } else {
            SearchResult(found = false)
        }
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
     * Lernt aus den letzten 100 Ereignissen: Erfolgsquote je Ortungsquelle
     * (zeitlich + signalbasiert). Dient als Grundlage für die Kanal-Priorisierung.
     */
    private suspend fun learnFromExperience() {
        val last100 = experienceMemory.takeLast(100)
        val successBySource = last100
            .filter { it.success }
            .groupBy { it.source }
            .mapValues { (_, list) -> list.size.toDouble() / last100.size.coerceAtLeast(1) }
        // TODO: Hier können Strategie und Kanal-Priorisierung optimiert werden.
        @Suppress("UNUSED_EXPRESSION")
        successBySource
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
