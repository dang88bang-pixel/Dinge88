package com.secureguard.enterprise.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Rekursive Lern-Engine (Machine Learning): erkennt zeitliche, räumliche und
 * Signal-Muster aus vergangenen Suchen, lernt aus Erfahrungen und optimiert
 * Abfrageintervall sowie externe Quellen-Nutzung.
 */
@Singleton
class LearningEngine @Inject constructor() {

    private val _patterns = MutableStateFlow<List<Pattern>>(emptyList())
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()

    private val _confidence = MutableStateFlow(0.5f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()

    private val experienceMemory = ArrayDeque<Experience>()
    private val maxMemorySize = 1000

    // ============ MUSTERERKENNUNG ============

    fun analyzePatterns(experiences: List<Experience>): List<Pattern> {
        val patterns = mutableListOf<Pattern>()
        patterns += analyzeTemporalPatterns(experiences)
        patterns += analyzeSpatialPatterns(experiences)
        patterns += analyzeSignalPatterns(experiences)
        patterns += analyzeCorrelations(experiences)
        return patterns
    }

    private fun analyzeTemporalPatterns(experiences: List<Experience>): List<Pattern> {
        val hourHits = experiences.filter { it.success }.groupBy {
            Calendar.getInstance().apply { time = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }
        if (experiences.isEmpty()) return emptyList()
        return hourHits.mapNotNull { (hour, hits) ->
            if (hits.size < 5) null
            else Pattern(
                id = UUID.randomUUID().toString(),
                type = PatternType.TEMPORAL,
                key = "hour_$hour",
                confidence = hits.size.toFloat() / experiences.size,
                weight = hits.size.toDouble()
            )
        }
    }

    private fun analyzeSpatialPatterns(experiences: List<Experience>): List<Pattern> {
        val locationHits = experiences.filter { it.success && it.latitude != null && it.longitude != null }
            .groupBy { spatialKey(it.latitude!!, it.longitude!!) }
        if (experiences.isEmpty()) return emptyList()
        return locationHits.mapNotNull { (key, hits) ->
            if (hits.size < 3) null
            else Pattern(
                id = UUID.randomUUID().toString(),
                type = PatternType.SPATIAL,
                key = key,
                confidence = hits.size.toFloat() / experiences.size,
                weight = hits.size.toDouble()
            )
        }
    }

    private fun analyzeSignalPatterns(experiences: List<Experience>): List<Pattern> {
        val rssiValues = experiences.filter { it.success }.map { it.rssi }
        if (rssiValues.isEmpty()) return emptyList()
        val avg = rssiValues.average()
        val variance = rssiValues.map { (it - avg).pow(2) }.average()
        return listOf(
            Pattern(
                id = UUID.randomUUID().toString(),
                type = PatternType.SIGNAL,
                key = "rssi_pattern",
                confidence = 0.7f,
                weight = avg,
                metadata = mapOf("stdDev" to sqrt(variance))
            )
        )
    }

    private fun analyzeCorrelations(experiences: List<Experience>): List<Pattern> {
        val byHour = experiences.groupBy {
            Calendar.getInstance().apply { time = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }
        return byHour.mapNotNull { (hour, list) ->
            val rate = list.count { it.success }.toDouble() / list.size
            if (rate > 0.7) {
                Pattern(
                    id = UUID.randomUUID().toString(),
                    type = PatternType.CORRELATION,
                    key = "success_hour_$hour",
                    confidence = rate.toFloat(),
                    weight = rate
                )
            } else null
        }
    }

    // ============ PRÄDIKTION ============

    /**
     * Grid key for a position, rounded to ~110 m, e.g. "51.227_6.773".
     *
     * [predictNextLocation] parses this key back into a coordinate pair, so the
     * format is pinned to `Locale.US`: with a decimal-comma locale the default
     * string conversion would emit "51,227", which no longer parses as a Double
     * and would silently disable every prediction.
     */
    private fun spatialKey(lat: Double, lon: Double): String =
        "${roundCoord(lat)}_${roundCoord(lon)}"

    /** Rounds to 3 decimals using a locale-independent US formatter. */
    private fun roundCoord(value: Double): String =
        String.format(java.util.Locale.US, "%.3f", round(value * 1000) / 1000)

    /**
     * Sagt den wahrscheinlichsten Aufenthaltsort anhand gelernter Muster voraus.
     *
     * Nur räumliche Muster tragen Koordinaten im Key; zeitliche Muster haben
     * Keys wie "hour_14" und ergeben keine Position.
     */
    fun predictNextLocation(currentTime: Date = Date()): Pair<Double, Double>? {
        val best = _patterns.value
            .filter { it.type == PatternType.SPATIAL }
            .maxByOrNull { it.confidence } ?: return null
        if (best.confidence < 0.5f) return null
        val coords = best.key.split("_")
        if (coords.size != 2) return null
        return try {
            val lat = coords[0].toDouble()
            val lon = coords[1].toDouble()
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) null
            else Pair(lat, lon)
        } catch (e: NumberFormatException) {
            null
        }
    }

    // ============ LERNEN ============

    fun learn(experience: Experience) {
        experienceMemory.addLast(experience)
        while (experienceMemory.size > maxMemorySize) {
            experienceMemory.removeFirst()
        }
        _patterns.value = analyzePatterns(experienceMemory.toList())
        val successRate = if (experienceMemory.isEmpty()) 0.5f
        else experienceMemory.count { it.success }.toFloat() / experienceMemory.size
        _confidence.value = successRate
    }

    // ============ OPTIMIERUNG ============

    /** Optimales Abfrageintervall in Sekunden (lernend). */
    fun getOptimalInterval(): Int {
        val rate = _confidence.value
        return when {
            rate > 0.8 -> 120
            rate > 0.6 -> 60
            else -> 30
        }
    }

    /** Sollen externe Quellen (APIs/Crowd) hinzugezogen werden? */
    fun shouldUseExternalSources(): Boolean = _confidence.value < 0.5f

    /** Erfolgswahrscheinlichkeit für ein bestimmtes Asset. */
    fun getSuccessProbability(assetId: String): Float {
        val assetExperiences = experienceMemory.filter { it.assetId == assetId }
        if (assetExperiences.isEmpty()) return 0.3f
        return assetExperiences.count { it.success }.toFloat() / assetExperiences.size
    }

    /**
     * Snapshot der gelernten Erfahrungen (max. [maxMemorySize], älteste zuerst).
     * Wird vom Agenten genutzt, um Muster über echte Daten zu analysieren.
     */
    fun recentExperiences(): List<Experience> = experienceMemory.toList()
}

/** Eine einzelne gelernte Erfahrung (Suchergebnis eines Assets). */
data class Experience(
    val assetId: String,
    val success: Boolean,
    val rssi: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Date = Date(),
    val sourceType: String = "UNKNOWN",
    val duration: Long = 0
)

/** Ein erkanntes Muster (zeitlich/räumlich/Signal/Korrelation). */
data class Pattern(
    val id: String,
    val type: PatternType,
    val key: String,
    val confidence: Float,
    val weight: Double,
    val metadata: Map<String, Any> = emptyMap()
)

enum class PatternType {
    TEMPORAL, SPATIAL, SIGNAL, CORRELATION, BEHAVIORAL
}
