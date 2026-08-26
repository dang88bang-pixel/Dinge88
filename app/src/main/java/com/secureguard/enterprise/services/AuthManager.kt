package com.secureguard.enterprise.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lokale Authentifizierung (PIN/Passwort) mit PBKDF2-Salted-Hash.
 *
 * Solange keine PIN eingerichtet ist, bleibt die App entsperrt
 * ([AuthState.enabled] = false). Nach [MAX_ATTEMPTS] Fehlversuchen wird die
 * App gesperrt; nach [AUTO_LOCK_MINUTES] Inaktivität sperrt sie sich
 * automatisch wieder ([refreshAutoLock], z. B. aus `onResume`).
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("secureguard_auth", Context.MODE_PRIVATE)

    data class AuthState(
        val enabled: Boolean = false,
        val locked: Boolean = false,
        val attemptsRemaining: Int = MAX_ATTEMPTS,
        val autoLockAfterMinutes: Int = AUTO_LOCK_MINUTES
    )

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val secureRandom = SecureRandom()

    fun isPinConfigured(): Boolean = prefs.contains(KEY_HASH)

    /** Richtet eine neue PIN ein (mind. 4 Zeichen) und sperrt die App. */
    fun configurePin(pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        prefs.edit()
            .putString(KEY_HASH, hash(pin, salt))
            .putString(KEY_SALT, salt.toBase64())
            .putLong(KEY_LOCKED_AT, System.currentTimeMillis())
            .remove(KEY_LAST_UNLOCK)
            .apply()
        _state.value = AuthState(
            enabled = true,
            locked = true,
            attemptsRemaining = MAX_ATTEMPTS
        )
        return true
    }

    /** Entsperrt die App bei korrekter PIN. */
    fun unlock(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null)?.fromBase64() ?: return false
        val ok = MessageDigest.isEqual(
            hash(pin, salt).toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
        if (ok) {
            prefs.edit()
                .remove(KEY_LOCKED_AT)
                .putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
                .apply()
            _state.value = _state.value.copy(locked = false, attemptsRemaining = MAX_ATTEMPTS)
        } else {
            val remaining = (_state.value.attemptsRemaining - 1).coerceAtLeast(0)
            // Bei falscher PIN bleibt die App IMMER gesperrt.
            // remaining==0 → zusätzlich Hard-Lock (keine weiteren Versuche bis Neustart/Reset).
            _state.value = _state.value.copy(
                locked = true,
                attemptsRemaining = remaining
            )
            if (remaining <= 0) {
                prefs.edit().putLong(KEY_LOCKED_AT, System.currentTimeMillis()).apply()
            }
        }
        return ok
    }

    /** Sperrt die App manuell. */
    fun lock() {
        prefs.edit().putLong(KEY_LOCKED_AT, System.currentTimeMillis()).apply()
        _state.value = _state.value.copy(locked = true)
    }

    /**
     * Auto-Lock nach [AUTO_LOCK_MINUTES] Inaktivität. Sollte aus dem
     * `onResume` der MainActivity aufgerufen werden.
     */
    fun refreshAutoLock() {
        if (!_state.value.enabled || _state.value.locked) return
        val lastUnlock = prefs.getLong(KEY_LAST_UNLOCK, 0L)
        if (lastUnlock > 0 &&
            System.currentTimeMillis() - lastUnlock > AUTO_LOCK_MINUTES * 60_000L
        ) {
            lock()
        }
    }

    /** Entfernt die PIN wieder. */
    fun disablePin() {
        prefs.edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .remove(KEY_LOCKED_AT)
            .remove(KEY_LAST_UNLOCK)
            .apply()
        _state.value = AuthState(enabled = false, locked = false)
    }

    private fun loadState(): AuthState {
        val enabled = isPinConfigured()
        // Mit eingerichteter PIN startet die App gesperrt.
        return AuthState(
            enabled = enabled,
            locked = enabled,
            attemptsRemaining = MAX_ATTEMPTS
        )
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(
                PBEKeySpec(
                    pin.toCharArray(),
                    salt,
                    ITERATIONS,
                    KEY_LENGTH_BITS
                )
            )
        return key.encoded.toBase64()
    }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val MAX_ATTEMPTS = 5
        const val AUTO_LOCK_MINUTES = 5
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256

        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_LOCKED_AT = "locked_at"
        private const val KEY_LAST_UNLOCK = "last_unlock"
    }
}
