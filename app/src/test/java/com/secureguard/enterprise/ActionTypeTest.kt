package com.secureguard.enterprise

import com.secureguard.enterprise.presentation.ui.common.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Die acht Tochter-/Geräteaktionen (1:1, keine simulierten Antworten). */
class ActionTypeTest {

    @Test
    fun eightDeviceCommandsExist() {
        assertEquals(8, ActionType.entries.size)
    }

    @Test
    fun noSceneCommandsLeakIntoDeviceCommands() {
        val scene = setOf("SWEEP", "FOCUS", "GEOFENCE", "FORCE", "ACTION")
        ActionType.entries.forEach { at ->
            assertNotEquals("Gerätekommando darf keine Szenenaktion sein", true, at.wireCommand in scene)
        }
    }
}
