package com.secureguard.enterprise.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.secureguard.enterprise.presentation.theme.*
import com.secureguard.enterprise.presentation.ui.about.AboutScreen
import com.secureguard.enterprise.presentation.ui.actions.ActionsScreen
import com.secureguard.enterprise.presentation.ui.agent.AgentConfigScreen
import com.secureguard.enterprise.presentation.ui.alerts.AlertsScreen
import com.secureguard.enterprise.presentation.ui.assets.*
import com.secureguard.enterprise.presentation.ui.dashboard.DashboardScreen
import com.secureguard.enterprise.presentation.ui.help.HelpScreen
import com.secureguard.enterprise.presentation.ui.map.MapScreen
import com.secureguard.enterprise.presentation.ui.nodes.NodeStatusScreen
import com.secureguard.enterprise.presentation.ui.ports.AutomaticPortViewScreen
import com.secureguard.enterprise.presentation.ui.security.SecurityScreen
import com.secureguard.enterprise.presentation.ui.sensorfusion.SensorFusionScreen
import com.secureguard.enterprise.presentation.ui.settings.SettingsScreen
import com.secureguard.enterprise.presentation.ui.slack.SlackScreen
import com.secureguard.enterprise.presentation.ui.tempmail.TempMailScreen
import com.secureguard.enterprise.presentation.ui.terminal.TerminalScreen
import com.secureguard.enterprise.presentation.ui.esp32.Esp32ConfigScreen
import com.secureguard.enterprise.presentation.ui.health.HealthScreen
import com.secureguard.enterprise.presentation.ui.system.SplashScreen
import com.secureguard.enterprise.presentation.ui.system.SystemStatusScreen

@Composable
fun SecureGuardApp() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val bottom = NavItem.bottomNavItems.any { it.route == route }

    Scaffold(bottomBar = {
        if (bottom) NavigationBar(containerColor = SurfaceCard, contentColor = Color.White) {
            NavItem.bottomNavItems.forEach { item ->
                val label = stringResource(item.labelRes)
                NavigationBarItem(
                    selected = route == item.route,
                    onClick = { navController.navigate(item.route) {
                        popUpTo(Routes.DASHBOARD) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }},
                    icon = { Icon(item.icon, label) }, label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan, selectedTextColor = AccentCyan,
                        indicatorColor = Color(0xFF163D5A),
                        unselectedIconColor = Color(0xFF8899AA), unselectedTextColor = Color(0xFF8899AA)
                    )
                )
            }
        }
    }) { padding ->
        NavHost(navController, Routes.DASHBOARD, Modifier.padding(padding)) {
            composable(Routes.DASHBOARD) { DashboardScreen(navController) }
            composable(Routes.ASSETS) { AssetListScreen(navController) }
            composable(Routes.MAP) { MapScreen(navController) }
            composable(Routes.ACTIONS) { ActionsScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.AGENT_CONFIG) { AgentConfigScreen(navController) }
            composable(Routes.ALERTS) { AlertsScreen(navController) }
            composable(Routes.ADD_ASSET) { AddAssetScreen(navController) }
            composable(Routes.SCAN_QR) { ScanQrScreen(navController) }
            composable(Routes.NODE_STATUS) { NodeStatusScreen(navController) }
            composable(Routes.TEMP_MAIL) { TempMailScreen(navController) }
            composable(Routes.TERMINAL) { TerminalScreen(navController) }
            composable(Routes.SENSOR_FUSION) { SensorFusionScreen(navController) }
            composable(Routes.SECURITY) { SecurityScreen(navController) }
            composable(Routes.ESP32_CONFIG) { Esp32ConfigScreen(navController) }
            composable(Routes.HEALTH) { HealthScreen(navController) }
            composable(Routes.SLACK) { SlackScreen(navController) }
            composable(Routes.PORTS) { AutomaticPortViewScreen(navController) }
            composable(Routes.HELP) { HelpScreen(navController) }
            composable(Routes.ABOUT) { AboutScreen(navController) }
            composable(Routes.SPLASH) { SplashScreen(navController) }
            composable(Routes.SYSTEM_STATUS) { SystemStatusScreen(navController) }
            composable(Routes.ASSET_DETAIL, arguments = listOf(navArgument("assetId") { type = NavType.StringType })) {
                AssetDetailScreen(navController, it.arguments?.getString("assetId").orEmpty())
            }
        }
    }
}
