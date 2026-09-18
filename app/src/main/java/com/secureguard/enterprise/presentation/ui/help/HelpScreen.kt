package com.secureguard.enterprise.presentation.ui.help

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.ui.common.ReferenceScreen

@Composable
fun HelpScreen(navController: NavController) {
    ReferenceScreen("Hilfe & Support", navController) {
        Text("SecureGuard Pro", style = MaterialTheme.typography.titleMedium)
        Text("Hier findest du Hinweise zu Verbindung, Berechtigungen, Assets und Diagnose.")
        Text("Für jeden Vorgang zeigt die App Lade-, Inhalts-, Leer- und Fehlerzustände.")
        Text("Bei Hardwareproblemen werden nicht verfügbare Funktionen als solche gekennzeichnet.")
    }
}
