package com.secureguard.enterprise.presentation.ui.common

import androidx.annotation.StringRes
import com.secureguard.enterprise.R

/** Commands that can be dispatched to a connected asset. */
enum class ActionType(val wireCommand: String, @StringRes val labelRes: Int) {
    ALARM("ALARM", R.string.ab_alarm),
    LIGHT("LIGHT", R.string.ab_blink),
    MOTOR_OFF("MOTOR_OFF", R.string.ab_motor),
    BATTERY("BATTERY", R.string.ab_battery),
    MESSAGE("MESSAGE", R.string.ab_message),
    POSITION("POSITION", R.string.ab_position),
    RESTART("RESTART", R.string.ab_restart),
    TELEMETRY("TELEMETRY", R.string.ab_telemetry)
}

/** Outcome of dispatching an [ActionType]. */
data class ActionResult(
    val success: Boolean,
    val message: String
) {
    companion object {
        val Processing = ActionResult(false, "…")
    }
}
