package com.secureguard.enterprise.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-Memory-Cache mit TTL und Größenbegrenzung (echtes LRU, O(1)).
 *
 * Bisher wurde bei jedem put/get die komplette Map kopiert (O(n) + GC-Druck).
 * Jetzt: synchronisiertes access-order-LinkedHashMap (removeEldestEntry).
 * Wird z. B. für Telemetrie-Zwischenwerte und API-Antworten genutzt.
 */
@Singleton
class CacheManager @Inject constructor() {

    private val lock = Any()

    private val lru = object : LinkedHashMap<String, CachedData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedData>): Boolean =
            size > MAX_ENTRIES
    }

    data class CachedData(
        val data: Any,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = synchronized(lock) {
        val entry = lru[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
            lru.remove(key)
            return null
        }
        entry.data as? T
    }

    fun put(key: String, data: Any) = synchronized(lock) {
        lru[key] = CachedData(data)
        Unit
    }

    fun getOrPut(key: String, ttlMs: Long = TTL_MS, producer: () -> Any): Any {
        get<Any>(key)?.let { return it }
        val value = producer()
        put(key, value)
        return value
    }

    fun invalidate(key: String) = synchronized(lock) {
        lru.remove(key)
        Unit
    }

    fun clear() = synchronized(lock) {
        lru.clear()
        Unit
    }

    fun size(): Int = synchronized(lock) { lru.size }

    companion object {
        const val MAX_ENTRIES = 100
        const val TTL_MS = 300_000L // 5 Minuten
    }
}
