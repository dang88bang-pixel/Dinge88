package com.secureguard.enterprise.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.navArgument
import com.secureguard.enterprise.presentation.ui.actions.ActionsScreen
import com.secureguard.enterprise.presentation.ui.agent.AgentConfigScreen
import com.secureguard.enterprise.presentation.ui.alerts.AlertsScreen
import com.secureguard.enterprise.presentation.ui.assets.AddAssetScreen
import com.secureguard.enterprise.presentation.ui.assets.AssetDetailScreen
import com.secureguard.enterprise.presentation.ui.assets.AssetListScreen
import com.secureguard.enterprise.presentation.ui.assets.ScanQrScreen
import com.secureguard.enterprise.presentation.ui.dashboard.DashboardScreen
import com.secureguard.enterprise.presentation.ui.map.MapScreen
import com.secureguard.enterprise.presentation.ui.nodes.NodeStatusScreen
import com.secureguard.enterprise.presentation.ui.settings.SettingsScreen
import com.secureguard.enterprise.presentation.ui.tempmail.TempMailScreen
import com.secureguard.enterprise.presentation.ui.terminal.TerminalScreen
import com.secureguard.enterprise.presentation.ui.sensorfusion.SensorFusionScreen
import com.secureguard.enterprise.presentation.ui.security.SecurityScreen
import com.secureguard.enterprise.presentation.ui.esp32.Esp32ConfigScreen
import com.secureguard.enterprise.presentation.ui.health.HealthScreen
import com.secureguard.enterprise.presentation.ui.ops.OpsCenter3DScreen

private const val ANIM_MS = 250

@Composable
fun SecureGuardApp(shellViewModel: AppShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = NavItem.bottomNavItems.any { it.route == currentRoute }
    // Offene Alarme werden am Dashboard-Tab angezeigt, damit sie auch von
    // anderen Top-Level-Screens aus sichtbar bleiben.
    val openAlerts by shellViewModel.unacknowledgedAlerts.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                                val badgeCount = if (item == NavItem.Dashboard) openAlerts else 0
                                if (badgeCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(if (badgeCount > 9) "9+" else "$badgeCount")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = "$label, $badgeCount offene Alarme"
                                        )
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = label)
                                }
                            },
                            label = { Text(label) }
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
            composable(Routes.DASHBOARD) {
                DashboardScreen(navController = navController)
            }
            composable(Routes.ASSETS) {
                AssetListScreen(navController = navController)
            }
            composable(Routes.MAP) {
                MapScreen(navController = navController)
            }
            composable(Routes.ACTIONS) {
                ActionsScreen(navController = navController)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(navController = navController)
            }
            composable(Routes.AGENT_CONFIG) {
                AgentConfigScreen(navController = navController)
            }
            composable(Routes.ALERTS) {
                AlertsScreen(navController = navController)
            }
            composable(Routes.ADD_ASSET) {
                AddAssetScreen(navController = navController)
            }
            composable(Routes.SCAN_QR) {
                ScanQrScreen(navController = navController)
            }
            composable(Routes.NODE_STATUS) {
                NodeStatusScreen(navController = navController)
            }
            composable(Routes.TEMP_MAIL) {
                TempMailScreen(navController = navController)
            }
            composable(Routes.TERMINAL) {
                TerminalScreen(navController = navController)
            }
            composable(Routes.SENSOR_FUSION) {
                SensorFusionScreen(navController = navController)
            }
            composable(Routes.SECURITY) {
                SecurityScreen(navController = navController)
            }
            composable(Routes.ESP32_CONFIG) {
                Esp32ConfigScreen(navController = navController)
            }
            composable(Routes.HEALTH) {
                HealthScreen(navController = navController)
            }
            composable(Routes.OPS_3D) {
                OpsCenter3DScreen(navController = navController)
            }
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
