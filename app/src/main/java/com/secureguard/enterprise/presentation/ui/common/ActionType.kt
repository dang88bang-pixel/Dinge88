package com.secureguard.enterprise.presentation.ui.common

/** Commands that can be dispatched to a connected asset. */
enum class ActionType(val wireCommand: String, val label: String) {
    ALARM("ALARM", "Alarm"),
    LIGHT("LIGHT", "Blinken"),
    MOTOR_OFF("MOTOR_OFF", "Motor aus"),
    BATTERY("BATTERY", "Batterie"),
    MESSAGE("MESSAGE", "Nachricht"),
    POSITION("POSITION", "Position"),
    RESTART("RESTART", "Neustarten"),
    TELEMETRY("TELEMETRY", "Telemetrie")
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
