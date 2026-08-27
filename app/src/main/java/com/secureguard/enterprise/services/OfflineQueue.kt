package com.secureguard.enterprise.services

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.PendingAction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-Queue: Aktionen werden bei fehlender Verbindung persistiert und
 * später zugestellt ([retryPending]). Die Warteschlange liegt in Room und
 * übersteht damit Neustarts.
 */
@Singleton
class OfflineQueue @Inject constructor(
    private val database: SecureGuardDatabase
) {

    private val gson = Gson()

    val pending: Flow<List<PendingAction>> = database.pendingActionDao().observeAll()

    suspend fun enqueue(actionType: String, assetMac: String, payload: Map<String, Any> = emptyMap()) {
        database.pendingActionDao().insert(
            PendingAction(
                actionType = actionType,
                assetMac = assetMac,
                payload = gson.toJson(payload)
            )
        )
    }

    suspend fun size(): Int = database.pendingActionDao().count()

    /**
     * Versucht, alle wartenden Aktionen zuzustellen.
     * @param executor liefert `true` bei Erfolg
     * @return Anzahl erfolgreich zugestellter Aktionen
     */
    suspend fun retryPending(executor: suspend (PendingAction) -> Boolean): Int {
        val dao = database.pendingActionDao()
        var delivered = 0
        for (action in dao.getAll()) {
            var execError: String? = null
            val ok = try {
                executor(action)
            } catch (e: Exception) {
                execError = e.message
                false
            }
            if (ok) {
                dao.deleteById(action.id)
                delivered++
            } else {
                // Auch ein `false` ohne Exception ist ein Versuch (F-61e):
                // sonst wird attempts nie erhöht und die Queue läuft endlos.
                dao.markAttempt(action.id, execError ?: "Executor lieferte false")
                // Dead-Letter: nach MAX_ATTEMPTS aus der Queue entfernen
                // (letzte Fehlermeldung bleibt im Log/Beobachter erhalten).
                if (action.attempts + 1 >= MAX_ATTEMPTS) {
                    android.util.Log.w(
                        "OfflineQueue",
                        "Dead-Letter nach ${action.attempts + 1} Versuchen: ${action.actionType} (${execError ?: "false"})"
                    )
                    dao.deleteById(action.id)
                }
            }
        }
        return delivered
    }

    suspend fun clear() {
        database.pendingActionDao().getAll().forEach { database.pendingActionDao().deleteById(it.id) }
    }

    /** Validiert, ob der Payload gültiges JSON ist (wird vor dem Einreihen geprüft). */
    fun isValidPayload(json: String): Boolean = try {
        JsonParser.parseString(json)
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
