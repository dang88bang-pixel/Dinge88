package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val batteryLevel = remember { getBatteryLevel(context) }
    val assets by viewModel.assets.collectAsState(initial = emptyList())
    val detections by viewModel.detections.collectAsState(initial = emptyList())
    val alerts by viewModel.alerts.collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(16.dp)) {
        StatCard(label = "Batterie", value = "$batteryLevel%", icon = "🔋")
        StatCard(label = "Assets", value = assets.size.toString(), icon = "📍")
        StatCard(label = "Detektionen", value = detections.size.toString(), icon = "🔍")
        StatCard(label = "Alarme", value = alerts.count { !it.resolved }.toString(), icon = "⚠️")
    }
}

fun getBatteryLevel(context: Context): Int {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        .let { chargeCounter ->
            val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            if (capacity > 0) (chargeCounter * 100) / capacity else 0
        }
}