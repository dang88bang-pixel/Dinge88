package com.secureguard.enterprise.util

import kotlinx.coroutines.delay

/**
 * Retry-Logik für Netzwerkausfälle: führt einen Block mit exponentiell
 * wachsendem Backoff erneut aus und wirft nach [maxAttempts] Versuchen die
 * letzte Exception.
 */
object RetryManager {

    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        baseDelayMs: Long = 1000,
        backoffFactor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var attempts = 0
        var lastError: Exception? = null
        var delayMs = baseDelayMs

        while (attempts < maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                attempts++
                if (attempts >= maxAttempts) break
                delay(delayMs)
                delayMs = (delayMs * backoffFactor).toLong()
            }
        }
        throw lastError ?: IllegalStateException("Max retries exceeded")
    }

    /** Fehlertolerante Variante: gibt bei Erschöpfung `null` zurück. */
    suspend fun <T> withRetryOrNull(
        maxAttempts: Int = 3,
        baseDelayMs: Long = 1000,
        block: suspend () -> T?
    ): T? = try {
        withRetry(maxAttempts, baseDelayMs, block = block)
    } catch (e: Exception) {
        null
    }
}
