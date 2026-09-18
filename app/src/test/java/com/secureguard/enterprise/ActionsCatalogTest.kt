package com.secureguard.enterprise

import com.secureguard.enterprise.presentation.ui.common.ACTIONS_CATALOG
import com.secureguard.enterprise.presentation.ui.common.ActionKind
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.presentation.ui.common.ActionRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrität des zentralen Aktionskatalogs (§7/§8):
 * - Alle acht Kotlin-Geräteaktionen sind 1:1 angebunden,
 * - Szenenaktionen (SWEEP/FOCUS/GEOFENCE/FORCE) bleiben SCENE-only und lösen
 *   niemals ein Gerätekommando aus.
 */
class ActionsCatalogTest {

    @Test
    fun allEightDeviceActionsAreWiredOneToOne() {
        val deviceActions = ACTIONS_CATALOG.filter { it.kind == ActionKind.DEVICE }
        val wiredCommands = deviceActions.mapNotNull { it.wireCommand }.map { it.wireCommand }.toSet()

        // 1:1 → acht Aktionen, acht unterschiedliche Wire-Commands.
        assertEquals(8, deviceActions.size)
        assertEquals(8, wiredCommands.size)

        // Jeder ActionType ist abgedeckt.
        ActionType.entries.forEach { at ->
            assertTrue(
                "ActionType.${at.wireCommand} fehlt im Katalog",
                at.wireCommand in wiredCommands
            )
        }
    }

    @Test
    fun sceneActionsNeverTriggerDeviceCommands() {
        val expectedScene = setOf("SWEEP", "FOCUS", "GEOFENCE", "FORCE", "ACTION")
        val sceneActions = ACTIONS_CATALOG.filter { it.key in expectedScene }

        assertEquals(5, sceneActions.size)
        sceneActions.forEach { def ->
            assertEquals(
                "$def.key muss SCENE-Kind sein",
                ActionKind.SCENE,
                def.kind
            )
            assertEquals(
                "$def.key darf kein Gerätekommando tragen",
                null,
                def.wireCommand
            )
        }
        // Kein Scene-Schlüssel kollidiert mit einem Gerätekommando.
        val deviceKeys = ActionType.entries.map { it.wireCommand }.toSet()
        expectedScene.forEach { key -> assertTrue(key !in deviceKeys) }
    }

    @Test
    fun restartIsCriticalAndRequiresConfirmation() {
        val restart = ACTIONS_CATALOG.firstOrNull { it.key == "RESTART" }
        assertNotNull(restart)
        assertEquals(ActionRisk.CRITICAL, restart?.risk)
        assertTrue(restart?.isCritical == true)
    }

    @Test
    fun catalogKeysAreUnique() {
        val keys = ACTIONS_CATALOG.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
