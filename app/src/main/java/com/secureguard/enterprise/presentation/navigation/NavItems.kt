package com.secureguard.enterprise.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations shown in the bottom navigation bar. */
sealed class NavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : NavItem("dashboard", "Dashboard", Icons.Default.Dashboard)
    data object Assets : NavItem("assets", "Assets", Icons.Default.LocalShipping)
    data object Map : NavItem("map", "Karte", Icons.Default.Map)
    data object Actions : NavItem("actions", "Aktionen", Icons.Default.Bolt)
    data object Settings : NavItem("settings", "Einstellungen", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Dashboard, Assets, Map, Actions, Settings)
    }
}

/** All named routes used across the app (including detail / config screens). */
object Routes {
    const val DASHBOARD = "dashboard"
    const val ASSETS = "assets"
    const val MAP = "map"
    const val ACTIONS = "actions"
    const val SETTINGS = "settings"
    const val AGENT_CONFIG = "agent_config"
    const val ALERTS = "alerts"
    const val ADD_ASSET = "add_asset"
    const val SCAN_QR = "scan_qr"
    const val ASSET_DETAIL = "asset_detail/{assetId}"
    const val NODE_STATUS = "node_status"
    const val TEMP_MAIL = "temp_mail"
    const val TERMINAL = "terminal"
    const val SENSOR_FUSION = "sensor_fusion"
    const val SECURITY = "security"
    const val ESP32_CONFIG = "esp32_config"
    fun assetDetail(id: String) = "asset_detail/$id"
}
