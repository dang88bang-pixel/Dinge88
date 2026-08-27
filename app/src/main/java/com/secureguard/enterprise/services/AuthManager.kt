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
        val autoLockAfterMinutes: Int = AUTO_LOCK_MINUTES,
        /** >0 = aktive Zeitsperre nach zu vielen Fehlversuchen. */
        val lockoutSecondsRemaining: Long = 0
    )

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Fehlversuche persistent – überleben den App-/Prozess-Neustart. */
    private var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()

    /** Auto-Lock-Dauer in Minuten (persistent, 1–60 Min, F-49). */
    var autoLockMinutes: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_MINUTES, AUTO_LOCK_MINUTES).coerceIn(1, 60)
        private set(value) = prefs.edit().putInt(KEY_AUTO_LOCK_MINUTES, value).apply()

    /** Setzt die Auto-Lock-Dauer (UI: Security-Center). */
    fun setAutoLockMinutes(minutes: Int) {
        autoLockMinutes = minutes.coerceIn(1, 60)
        _state.value = _state.value.copy(autoLockAfterMinutes = autoLockMinutes)
    }

    private val secureRandom = SecureRandom()

    fun isPinConfigured(): Boolean = prefs.contains(KEY_HASH)

    /** Richtet eine neue PIN ein (mind. 4 Zeichen) und sperrt die App. */
    fun configurePin(pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        prefs.edit()
            .putString(KEY_HASH, hash(pin, salt))
            .putString(KEY_SALT, salt.toBase64())
            .putLong(KEY_LOCKED_AT, 0L)
            .remove(KEY_LAST_UNLOCK)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .apply()
        _state.value = AuthState(
            enabled = true,
            locked = true,
            attemptsRemaining = MAX_ATTEMPTS
        )
        return true
    }

    /**
     * Entsperrt die App bei korrekter PIN.
     * Nach [MAX_ATTEMPTS] Fehlversuchen greift eine exponentielle Zeitsperre
     * ([LOCKOUT_BASE_MINUTES] × 2^(überschrittene Batches)); der Versuchszähler
     * ist persistent und überlebt den App-Neustart.
     */
    fun unlock(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = prefs.getString(KEY_SALT, null)?.fromBase64() ?: return false

        // Aktive Zeitsperre? → PIN-Eingabe wird abgewiesen, ohne den Hash zu prüfen.
        if (lockoutRemainingSeconds() > 0) {
            _state.value = _state.value.copy(
                locked = true,
                attemptsRemaining = 0,
                lockoutSecondsRemaining = lockoutRemainingSeconds()
            )
            return false
        }

        val ok = MessageDigest.isEqual(
            hash(pin, salt).toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
        if (ok) {
            failedAttempts = 0
            prefs.edit()
                .remove(KEY_LOCKED_AT)
                .putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
                .apply()
            _state.value = _state.value.copy(
                locked = false,
                attemptsRemaining = MAX_ATTEMPTS,
                autoLockAfterMinutes = autoLockMinutes,
                lockoutSecondsRemaining = 0
            )
        } else {
            val attempts = failedAttempts + 1
            failedAttempts = attempts
            val remaining = (MAX_ATTEMPTS - attempts).coerceAtLeast(0)
            _state.value = _state.value.copy(
                locked = true,
                attemptsRemaining = remaining
            )
            if (attempts >= MAX_ATTEMPTS) {
                // Zeitsperre speichern: Basis × 2^(abgeschlossene Extra-Batches)
                val extraBatches = ((attempts - MAX_ATTEMPTS) / MAX_ATTEMPTS)
                val lockoutMs = LOCKOUT_BASE_MINUTES * 60_000L * (1L shl extraBatches.coerceAtMost(6))
                prefs.edit()
                    .putLong(KEY_LOCKED_AT, System.currentTimeMillis() + lockoutMs)
                    .apply()
                _state.value = _state.value.copy(
                    attemptsRemaining = 0,
                    lockoutSecondsRemaining = lockoutMs / 1000L
                )
            }
        }
        return ok
    }

    /** Verbleibende Sekunden der aktuellen Zeitsperre (0 = keine Sperre). */
    fun lockoutRemainingSeconds(): Long {
        val until = prefs.getLong(KEY_LOCKED_AT, 0L)
        if (until <= 0L) return 0L
        val remaining = (until - System.currentTimeMillis()) / 1000L
        return if (remaining > 0) remaining else 0L
    }

    /** Sperrt die App manuell (ohne Zeitsperre – Entsperren mit PIN jederzeit möglich). */
    fun lock() {
        prefs.edit().putLong(KEY_LOCKED_AT, 0L).apply()
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
            System.currentTimeMillis() - lastUnlock > autoLockMinutes * 60_000L
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
            .remove(KEY_FAILED_ATTEMPTS)
            .apply()
        _state.value = AuthState(enabled = false, locked = false)
    }

    private fun loadState(): AuthState {
        val enabled = isPinConfigured()
        // Mit eingerichteter PIN startet die App gesperrt; ein aktiver Lockout
        // (exponentielle Zeitsperre nach Fehlversuchen) bleibt über Neustarts aktiv.
        val lockout = lockoutRemainingSeconds()
        val usedAttempts = failedAttempts.coerceIn(0, MAX_ATTEMPTS)
        return AuthState(
            enabled = enabled,
            locked = enabled,
            attemptsRemaining = if (lockout > 0) 0 else MAX_ATTEMPTS - usedAttempts,
            autoLockAfterMinutes = autoLockMinutes,
            lockoutSecondsRemaining = lockout
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

        /** Basis-Dauer der Zeitsperre nach Erreichen von MAX_ATTEMPTS. */
        const val LOCKOUT_BASE_MINUTES = 1L
        private const val ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256

        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_LOCKED_AT = "locked_at"
        private const val KEY_LAST_UNLOCK = "last_unlock"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
    }
}
