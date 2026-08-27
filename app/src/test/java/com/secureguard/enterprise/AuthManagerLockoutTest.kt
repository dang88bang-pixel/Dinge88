package com.secureguard.enterprise

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.secureguard.enterprise.services.AuthManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lockout-/Auto-Lock-Pfade des [AuthManager] auf einer **modernen** API-Ebene
 * (SDK 34) – Ergänzung zu [AuthManagerTest] (SDK 28), damit die seit dem
 * Audit (FEHLER_MANGEL_LISTE F-03/F-49) neuen Pfade (persistenter Zähler,
 * exponentielle Zeitsperre, konfigurierbares Auto-Lock) auch auf aktuellen
 * Android-Ständen geprüft werden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthManagerLockoutTest {

    private lateinit var auth: AuthManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("secureguard_auth", Context.MODE_PRIVATE)
            .edit().clear().commit()
        auth = AuthManager(context)
    }

    @Test
    fun lockout_persists_across_new_instance() {
        auth.configurePin("1357")
        repeat(AuthManager.MAX_ATTEMPTS) { auth.unlock("0000") }
        assertThat(auth.lockoutRemainingSeconds()).isGreaterThan(0L)

        // Neuer AuthManager (≈ App-Neustart): Zeitsperre bleibt aktiv,
        // Versuchszähler wird NICHT zurückgesetzt.
        val fresh = AuthManager(
            ApplicationProvider.getApplicationContext<Context>()
        )
        assertThat(fresh.state.value.locked).isTrue()
        assertThat(fresh.state.value.attemptsRemaining).isEqualTo(0)
        assertThat(fresh.lockoutRemainingSeconds()).isGreaterThan(0L)
    }

    @Test
    fun lockout_blocks_pin_entry_but_keeps_state() {
        auth.configurePin("2468")
        repeat(AuthManager.MAX_ATTEMPTS) { auth.unlock("1111") }
        val remaining = auth.lockoutRemainingSeconds()
        assertThat(remaining).isGreaterThan(0L)

        // Selbst die KORREKTE PIN wird während der Zeitsperre abgewiesen
        assertThat(auth.unlock("2468")).isFalse()
        assertThat(auth.state.value.locked).isTrue()
        assertThat(auth.lockoutRemainingSeconds()).isAtMost(remaining)
    }

    @Test
    fun correct_pin_resets_counter_and_lockout_after_expiry() {
        auth.configurePin("9999")
        repeat(AuthManager.MAX_ATTEMPTS) { auth.unlock("0000") }
        assertThat(auth.state.value.attemptsRemaining).isEqualTo(0)

        // Zeitsperre simuliert abgelaufen (KEY_LOCKED_AT in die Vergangenheit)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("secureguard_auth", Context.MODE_PRIVATE)
            .edit().putLong("locked_at", System.currentTimeMillis() - 1_000L).commit()

        assertThat(auth.unlock("9999")).isTrue()
        assertThat(auth.state.value.locked).isFalse()
        assertThat(auth.state.value.attemptsRemaining).isEqualTo(AuthManager.MAX_ATTEMPTS)
    }

    @Test
    fun autoLockMinutes_configurable_and_bounded() {
        auth.setAutoLockMinutes(30)
        assertThat(auth.state.value.autoLockAfterMinutes).isEqualTo(30)
        assertThat(auth.autoLockMinutes).isEqualTo(30)

        // Clamp auf 1–60
        auth.setAutoLockMinutes(500)
        assertThat(auth.autoLockMinutes).isEqualTo(60)
        auth.setAutoLockMinutes(0)
        assertThat(auth.autoLockMinutes).isEqualTo(1)
    }

    @Test
    fun manual_lock_does_not_trigger_lockout() {
        auth.configurePin("4242")
        auth.unlock("4242")
        auth.lock()
        assertThat(auth.state.value.locked).isTrue()
        assertThat(auth.lockoutRemainingSeconds()).isEqualTo(0L)
    }
}
