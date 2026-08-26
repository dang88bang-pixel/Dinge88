package com.secureguard.enterprise

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureguard.enterprise.presentation.theme.SecureGuardTheme
import com.secureguard.enterprise.presentation.ui.auth.LockScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose-UI: Lock-Screen PIN-Eingabe.
 *
 * Die eingegebene PIN kommt **vom Anwender** (hier: Test-Fixture „2468“).
 * Keine generierten Produktions-Passwörter.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lockScreen_shows_prompt_and_fields() {
        composeRule.setContent {
            SecureGuardTheme {
                LockScreen(attemptsRemaining = 5, onUnlock = { false })
            }
        }
        composeRule.onNodeWithText("App gesperrt – PIN eingeben").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_pin_field").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_unlock_button").assertIsDisplayed()
    }

    @Test
    fun unlock_button_forwards_user_pin() {
        var received: String? = null
        composeRule.setContent {
            SecureGuardTheme {
                LockScreen(attemptsRemaining = 5, onUnlock = { pin ->
                    received = pin
                    pin == "2468"
                })
            }
        }
        // Anwender-PIN (Test-Fixture) – in Prod setzt der User seine eigene PIN
        composeRule.onNodeWithTag("lock_pin_field").performTextInput("2468")
        composeRule.onNodeWithTag("lock_unlock_button").performClick()
        assertEquals("2468", received)
    }

    @Test
    fun wrong_pin_shows_error() {
        composeRule.setContent {
            SecureGuardTheme {
                LockScreen(attemptsRemaining = 3, onUnlock = { false })
            }
        }
        composeRule.onNodeWithTag("lock_pin_field").performTextInput("0000")
        composeRule.onNodeWithTag("lock_unlock_button").performClick()
        composeRule.onNodeWithText("Falsche PIN – noch 3 Versuche", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun correct_pin_clears_field() {
        var unlocked = false
        composeRule.setContent {
            SecureGuardTheme {
                LockScreen(attemptsRemaining = 5, onUnlock = { pin ->
                    unlocked = pin == "1357"
                    unlocked
                })
            }
        }
        composeRule.onNodeWithTag("lock_pin_field").performTextInput("1357")
        composeRule.onNodeWithTag("lock_unlock_button").performClick()
        assertTrue(unlocked)
    }
}
