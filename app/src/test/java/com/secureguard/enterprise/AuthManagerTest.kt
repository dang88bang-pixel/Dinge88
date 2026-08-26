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
 * PIN/Login-Logik. Test-PINs sind **nur** disposable Fixture-Werte –
 * Produktions-PINs und Keystore-Passwörter legt der Anwender selbst fest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AuthManagerTest {

    private lateinit var auth: AuthManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("secureguard_auth", Context.MODE_PRIVATE)
            .edit().clear().commit()
        auth = AuthManager(context)
    }

    @Test
    fun initially_noPin_unlocked() {
        assertThat(auth.isPinConfigured()).isFalse()
        assertThat(auth.state.value.enabled).isFalse()
        assertThat(auth.state.value.locked).isFalse()
    }

    @Test
    fun configurePin_rejects_too_short() {
        // Anwender muss mind. 4 Zeichen wählen
        assertThat(auth.configurePin("12")).isFalse()
        assertThat(auth.isPinConfigured()).isFalse()
    }

    @Test
    fun configurePin_locks_app() {
        val userPin = "2468" // nur Test-Fixture
        assertThat(auth.configurePin(userPin)).isTrue()
        assertThat(auth.isPinConfigured()).isTrue()
        assertThat(auth.state.value.enabled).isTrue()
        assertThat(auth.state.value.locked).isTrue()
    }

    @Test
    fun unlock_with_correct_pin() {
        val userPin = "1357"
        auth.configurePin(userPin)
        assertThat(auth.unlock(userPin)).isTrue()
        assertThat(auth.state.value.locked).isFalse()
        assertThat(auth.state.value.attemptsRemaining).isEqualTo(AuthManager.MAX_ATTEMPTS)
    }

    @Test
    fun unlock_wrong_pin_decrements_attempts() {
        auth.configurePin("9999")
        assertThat(auth.unlock("0000")).isFalse()
        assertThat(auth.state.value.locked).isTrue()
        assertThat(auth.state.value.attemptsRemaining).isEqualTo(AuthManager.MAX_ATTEMPTS - 1)
    }

    @Test
    fun lock_after_manual_lock() {
        auth.configurePin("4242")
        auth.unlock("4242")
        auth.lock()
        assertThat(auth.state.value.locked).isTrue()
    }

    @Test
    fun disablePin_clears_configuration() {
        auth.configurePin("5555")
        auth.disablePin()
        assertThat(auth.isPinConfigured()).isFalse()
        assertThat(auth.state.value.enabled).isFalse()
        assertThat(auth.state.value.locked).isFalse()
    }

    @Test
    fun exhausted_attempts_remain_locked() {
        auth.configurePin("7777")
        repeat(AuthManager.MAX_ATTEMPTS) {
            assertThat(auth.unlock("0000")).isFalse()
        }
        assertThat(auth.state.value.attemptsRemaining).isEqualTo(0)
        assertThat(auth.state.value.locked).isTrue()
        // weitere Fehlversuche bleiben gesperrt
        assertThat(auth.unlock("0000")).isFalse()
        assertThat(auth.state.value.locked).isTrue()
    }
}
