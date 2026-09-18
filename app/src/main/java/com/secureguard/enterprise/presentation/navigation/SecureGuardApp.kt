package com.secureguard.enterprise.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.secureguard.enterprise.presentation.theme.AccentCyan
import com.secureguard.enterprise.presentation.theme.AccentRed
import com.secureguard.enterprise.presentation.theme.SurfaceCard
import com.secureguard.enterprise.presentation.ui.about.AboutScreen
import com.secureguard.enterprise.presentation.ui.actions.ActionsScreen
import com.secureguard.enterprise.presentation.ui.agent.AgentConfigScreen
import com.secureguard.enterprise.presentation.ui.alerts.AlertsScreen
import com.secureguard.enterprise.presentation.ui.alerts.AlertsViewModel
import com.secureguard.enterprise.presentation.ui.assets.AddAssetScreen
import com.secureguard.enterprise.presentation.ui.assets.AssetDetailScreen
import com.secureguard.enterprise.presentation.ui.assets.AssetListScreen
import com.secureguard.enterprise.presentation.ui.assets.ScanQrScreen
import com.secureguard.enterprise.presentation.ui.dashboard.DashboardScreen
import com.secureguard.enterprise.presentation.ui.esp32.Esp32ConfigScreen
import com.secureguard.enterprise.presentation.ui.health.HealthScreen
import com.secureguard.enterprise.presentation.ui.help.HelpScreen
import com.secureguard.enterprise.presentation.ui.map.MapScreen
import com.secureguard.enterprise.presentation.ui.nodes.NodeStatusScreen
import com.secureguard.enterprise.presentation.ui.opscenter.OpsCenter3DScreen
import com.secureguard.enterprise.presentation.ui.ports.AutomaticPortViewScreen
import com.secureguard.enterprise.presentation.ui.security.SecurityScreen
import com.secureguard.enterprise.presentation.ui.sensorfusion.SensorFusionScreen
import com.secureguard.enterprise.presentation.ui.settings.SettingsScreen
import com.secureguard.enterprise.presentation.ui.slack.SlackScreen
import com.secureguard.enterprise.presentation.ui.system.SplashScreen
import com.secureguard.enterprise.presentation.ui.system.SystemStatusScreen
import com.secureguard.enterprise.presentation.ui.tempmail.TempMailScreen
import com.secureguard.enterprise.presentation.ui.terminal.TerminalScreen

private const val ANIM_MS = 250

@Composable
fun SecureGuardApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = NavItem.bottomNavItems.any { it.route == currentRoute }

    // Offene (unbestätigte) Alarme → Badge in der Bottom-Navigation (§4).
    val alertsViewModel: AlertsViewModel = hiltViewModel()
    val openAlerts by alertsViewModel.openAlertCount.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SurfaceCard,
                    contentColor = Color.White
                ) {
                    NavItem.bottomNavItems.forEach { item ->
                        val label = stringResource(item.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.DASHBOARD) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (openAlerts > 0) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = AccentRed) {
                                            Text(if (openAlerts > 99) "99+" else openAlerts.toString())
                                        }
                                    }) {
                                        Icon(item.icon, contentDescription = label)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = label)
                                }
                            },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                indicatorColor = Color(0xFF163D5A),
                                unselectedIconColor = Color(0xFF8899AA),
                                unselectedTextColor = Color(0xFF8899AA)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideInHorizontally(animationSpec = tween(ANIM_MS)) { it } },
            exitTransition = { slideOutHorizontally(animationSpec = tween(ANIM_MS)) { -it / 4 } },
            popEnterTransition = { slideInHorizontally(animationSpec = tween(ANIM_MS)) { -it / 4 } },
            popExitTransition = { slideOutHorizontally(animationSpec = tween(ANIM_MS)) { it } }
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen(navController = navController) }
            composable(Routes.ASSETS) { AssetListScreen(navController = navController) }
            composable(Routes.MAP) { MapScreen(navController = navController) }
            composable(Routes.ACTIONS) { ActionsScreen(navController = navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController = navController) }
            composable(Routes.AGENT_CONFIG) { AgentConfigScreen(navController = navController) }
            composable(Routes.ALERTS) { AlertsScreen(navController = navController) }
            composable(Routes.ADD_ASSET) { AddAssetScreen(navController = navController) }
            composable(Routes.SCAN_QR) { ScanQrScreen(navController = navController) }
            composable(Routes.NODE_STATUS) { NodeStatusScreen(navController = navController) }
            composable(Routes.TEMP_MAIL) { TempMailScreen(navController = navController) }
            composable(Routes.TERMINAL) { TerminalScreen(navController = navController) }
            composable(Routes.SENSOR_FUSION) { SensorFusionScreen(navController = navController) }
            composable(Routes.SECURITY) { SecurityScreen(navController = navController) }
            composable(Routes.ESP32_CONFIG) { Esp32ConfigScreen(navController = navController) }
            composable(Routes.HEALTH) { HealthScreen(navController = navController) }
            composable(Routes.SLACK) { SlackScreen(navController = navController) }
            composable(Routes.PORTS) { AutomaticPortViewScreen(navController = navController) }
            composable(Routes.HELP) { HelpScreen(navController = navController) }
            composable(Routes.ABOUT) { AboutScreen(navController = navController) }
            composable(Routes.SPLASH) { SplashScreen(navController = navController) }
            composable(Routes.SYSTEM_STATUS) { SystemStatusScreen(navController = navController) }
            composable(Routes.OPS_3D) { OpsCenter3DScreen(navController = navController) }
            composable(
                route = Routes.ASSET_DETAIL,
                arguments = listOf(navArgument("assetId") { type = NavType.StringType })
            ) { entry ->
                AssetDetailScreen(
                    navController = navController,
                    assetId = entry.arguments?.getString("assetId").orEmpty()
                )
            }
        }
    }
}
