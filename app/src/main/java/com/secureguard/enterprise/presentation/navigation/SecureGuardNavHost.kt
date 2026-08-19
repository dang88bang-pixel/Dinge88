package com.secureguard.enterprise.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secureguard.enterprise.presentation.ui.actions.ActionsScreen
import com.secureguard.enterprise.presentation.ui.agent.AgentConfigScreen
import com.secureguard.enterprise.presentation.ui.assets.AssetDetailScreen
import com.secureguard.enterprise.presentation.ui.assets.AssetListScreen
import com.secureguard.enterprise.presentation.ui.dashboard.DashboardScreen
import com.secureguard.enterprise.presentation.ui.map.MapScreen
import com.secureguard.enterprise.presentation.ui.settings.SettingsScreen

sealed class NavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : NavItem("dashboard", "Dashboard", Icons.Filled.Speed)
    data object Assets : NavItem("assets", "Assets", Icons.Filled.LocalShipping)
    data object Map : NavItem("map", "Karte", Icons.Filled.Map)
    data object Actions : NavItem("actions", "Aktionen", Icons.Filled.Warning)
    data object Settings : NavItem("settings", "Einstellungen", Icons.Filled.Settings)
}

@Composable
fun SecureGuardNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val items = listOf(
        NavItem.Dashboard,
        NavItem.Assets,
        NavItem.Map,
        NavItem.Actions,
        NavItem.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy
                            ?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavItem.Dashboard.route) { DashboardScreen() }
            composable(NavItem.Assets.route) { AssetListScreen(navController) }
            composable(NavItem.Map.route) { MapScreen() }
            composable(NavItem.Actions.route) { ActionsScreen() }
            composable(NavItem.Settings.route) { SettingsScreen(navController) }
            composable("asset_detail/{assetId}") { backStackEntry ->
                val assetId = backStackEntry.arguments?.getString("assetId")
                if (assetId != null) {
                    AssetDetailScreen(assetId)
                }
            }
            composable("agent_config") { AgentConfigScreen() }
        }
    }
}
