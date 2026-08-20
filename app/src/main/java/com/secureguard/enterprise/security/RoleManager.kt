package com.secureguard.enterprise.security

/**
 * Berechtigungsmanagement (RBAC): Rollen → Berechtigungen.
 * Wird vom Audit-Log und den Aktionen genutzt; die App selbst läuft
 * aktuell im ADMIN-Kontext (Einzelgerät), die Rollen sind für die
 * Server-/Multi-User-Anbindung vorbereitet.
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

object RoleManager {

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

    fun hasPermission(user: User, permission: Permission): Boolean =
        user.permissions.contains(permission) ||
            rolePermissions[user.role]?.contains(permission) == true

    fun permissionsFor(role: Role): List<Permission> = rolePermissions[role] ?: emptyList()
}
