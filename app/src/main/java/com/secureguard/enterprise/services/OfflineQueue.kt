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
            // `attempts` comes from the snapshot taken before this run, so count
            // the attempt we are about to make.
            val exhausted = action.attempts + 1 >= MAX_ATTEMPTS
            val ok = try {
                executor(action)
            } catch (e: Exception) {
                dao.markAttempt(action.id, e.message)
                false
            }
            when {
                ok -> {
                    dao.deleteById(action.id)
                    delivered++
                }
                // After the last allowed attempt the entry is dropped; otherwise
                // it would be retried on every cycle forever.
                exhausted -> dao.deleteById(action.id)
                else -> dao.markAttempt(action.id, "Zustellung fehlgeschlagen")
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
