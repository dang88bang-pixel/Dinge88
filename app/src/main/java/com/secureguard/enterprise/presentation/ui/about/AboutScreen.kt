package com.secureguard.enterprise.presentation.ui.about

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.ui.common.ReferenceScreen

@Composable
fun AboutScreen(navController: NavController) {
    ReferenceScreen("Über SecureGuard", navController) {
        Text("SecureGuard Pro", style = MaterialTheme.typography.titleMedium)
        Text("Enterprise Security & Asset Operations")
        Text("Version und Build-Informationen werden aus der App-Konfiguration bezogen.")
        Text("Keine Zugangsdaten oder geheimen Schlüssel werden in dieser Oberfläche angezeigt.")
    }
}
