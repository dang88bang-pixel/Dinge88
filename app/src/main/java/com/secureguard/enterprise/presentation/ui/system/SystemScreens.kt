package com.secureguard.enterprise.presentation.ui.system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.ui.common.ReferenceScreen

@Composable
fun SplashScreen(navController: NavController) {
    ReferenceScreen("SecureGuard Pro") {
        Icon(Icons.Default.Security, contentDescription = "SecureGuard")
        Text("Initialisierung", style = MaterialTheme.typography.titleLarge)
        Text("Dienste, lokale Datenbank und sichere Verbindungen werden vorbereitet.")
    }
}

@Composable
fun SystemStatusScreen(navController: NavController) {
    ReferenceScreen("Systemstatus", navController) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Systemstatus")
        Text("Systembereit", style = MaterialTheme.typography.titleLarge)
        Text("Die Oberfläche verwendet den zentralen SecureGuard-Designstandard.")
        Text("Details zu Diensten und Hardware werden aus den jeweiligen Modulen geladen.")
    }
}
