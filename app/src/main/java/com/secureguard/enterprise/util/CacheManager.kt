package com.secureguard.enterprise.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-Memory-Cache mit TTL und Größenbegrenzung (LRU-ähnlich). Wird z. B. für
 * Telemetrie-Zwischenwerte und API-Antworten genutzt, um Wiederholungs-
 * Abfragen zu vermeiden.
 */
@Singleton
class CacheManager @Inject constructor() {

    data class CachedData(
        val data: Any,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _cache = MutableStateFlow<Map<String, CachedData>>(emptyMap())
    val cache: StateFlow<Map<String, CachedData>> = _cache.asStateFlow()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = _cache.value[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
            invalidate(key)
            return null
        }
        return entry.data as? T
    }

    fun put(key: String, data: Any) {
        val next = _cache.value.toMutableMap()
        next[key] = CachedData(data)
        // Größenbegrenzung: ältesten Eintrag entfernen
        while (next.size > MAX_ENTRIES) {
            val oldest = next.minByOrNull { it.value.timestamp } ?: break
            next.remove(oldest.key)
        }
        _cache.value = next
    }

    fun getOrPut(key: String, ttlMs: Long = TTL_MS, producer: () -> Any): Any {
        get<Any>(key)?.let { return it }
        val value = producer()
        put(key, value)
        return value
    }

    fun invalidate(key: String) {
        val next = _cache.value.toMutableMap()
        next.remove(key)
        _cache.value = next
    }

    fun clear() {
        _cache.value = emptyMap()
    }

    companion object {
        const val MAX_ENTRIES = 100
        const val TTL_MS = 300_000L // 5 Minuten
    }
}
