package com.secureguard.enterprise.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.graphics.vector.ImageVector
import com.secureguard.enterprise.R

/** Top-level destinations shown in the bottom navigation bar (i18n via @StringRes). */
sealed class NavItem(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    data object Dashboard : NavItem("dashboard", R.string.nav_dashboard, Icons.Default.Dashboard)
    data object Assets : NavItem("assets", R.string.nav_assets, Icons.Default.LocalShipping)
    data object Map : NavItem("map", R.string.nav_map, Icons.Default.Map)
    data object Actions : NavItem("actions", R.string.nav_actions, Icons.Default.Bolt)
    data object Settings : NavItem("settings", R.string.nav_settings, Icons.Default.Settings)

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
    const val HEALTH = "health"
    const val SLACK = "slack"
    fun assetDetail(id: String) = "asset_detail/$id"
}
