package com.secureguard.enterprise.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Berechtigungsmanagement (RBAC): Rollen → Berechtigungen.
 *
 * Seit F-44 wird die Rolle NICHT mehr hart auf ADMIN fixiert: die aktive
 * Rolle ist persistent (Prefs, Default ADMIN) und wird an allen
 * Mutations-Sites geprüft (Asset anlegen, Aktionen senden, Konfiguration,
 * Rollenwechsel). Ein Wechsel der Rolle verlangt MANAGE_USERS.
 */
enum class Role {
    ADMIN,      // Vollzugriff
    MANAGER,    // Alle Assets, keine Konfiguration
    OPERATOR,   // Eigene Assets
    VIEWER      // Nur Lesen
}

enum class Permission {
    VIEW_ASSETS,
    EDIT_ASSETS,
    DELETE_ASSETS,
    EXECUTE_ACTIONS,
    VIEW_LOGS,
    CONFIGURE_AGENT,
    MANAGE_USERS
}

data class User(
    val id: String,
    val name: String,
    val role: Role,
    val permissions: List<Permission> = emptyList()
)

@Singleton
class RoleManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _role = MutableStateFlow(loadRole())
    /** Aktive Rolle (persistent, Default ADMIN). */
    val role: StateFlow<Role> = _role.asStateFlow()

    val currentRole: Role get() = _role.value

    /** Setzt die aktive Rolle. Aufrufer muss MANAGE_USERS haben (F-44). */
    fun setRole(newRole: Role) {
        prefs.edit().putString(KEY_ROLE, newRole.name).apply()
        _role.value = newRole
    }

    /** Prüft die Berechtigung gegen die AKTUELLE Rolle. */
    fun has(permission: Permission): Boolean =
        rolePermissions[currentRole]?.contains(permission) == true

    /**
     * Prüft und dokumentiert: `false` = verweigert (Aufrufer soll das
     * dem Nutzer melden und in den Audit-Log schreiben).
     */
    fun require(permission: Permission): Boolean = has(permission)

    fun permissionsFor(role: Role): List<Permission> = rolePermissions[role] ?: emptyList()

    private fun loadRole(): Role =
        runCatching { Role.valueOf(prefs.getString(KEY_ROLE, null) ?: Role.ADMIN.name) }
            .getOrDefault(Role.ADMIN)

    private val rolePermissions: Map<Role, List<Permission>> = mapOf(
        Role.ADMIN to Permission.entries,
        Role.MANAGER to listOf(
            Permission.VIEW_ASSETS,
            Permission.EDIT_ASSETS,
            Permission.EXECUTE_ACTIONS,
            Permission.VIEW_LOGS
        ),
        Role.OPERATOR to listOf(
            Permission.VIEW_ASSETS,
            Permission.EXECUTE_ACTIONS
        ),
        Role.VIEWER to listOf(Permission.VIEW_ASSETS)
    )

    companion object {
        private const val PREFS = "secureguard_roles"
        private const val KEY_ROLE = "active_role"
    }
}
