package com.secureguard.enterprise.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.designsystem.Sg
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgMeter
import com.secureguard.enterprise.presentation.designsystem.SgPill
import com.secureguard.enterprise.presentation.designsystem.SgSignalBars
import com.secureguard.enterprise.presentation.designsystem.SgStatusDot
import com.secureguard.enterprise.presentation.designsystem.batteryColor
import com.secureguard.enterprise.presentation.designsystem.relativeTime
import com.secureguard.enterprise.presentation.designsystem.statusColor
import com.secureguard.enterprise.presentation.designsystem.statusLabel
import com.secureguard.enterprise.util.AccessibilityHelper

/**
 * Asset-Karte mit Sofort-Information: Status, Signalqualität, Ladestand,
 * letzte Sichtung – plus zwei Direktaktionen (Orten, Alarm) ohne Umweg über
 * den Detail-Screen.
 */
@Composable
fun AssetCard(
    asset: Asset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLocate: (() -> Unit)? = null,
    onAlarm: (() -> Unit)? = null
) {
    val color = statusColor(asset.status)
    val a11yStatus = AccessibilityHelper.contentDescriptionForStatus(asset.status.name)

    SgCard(
        modifier = modifier.fillMaxWidth(),
        accent = color,
        selected = selected,
        onClick = onClick,
        contentPadding = PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SgStatusDot(
                color = color,
                live = asset.status == AssetStatus.ONLINE,
                modifier = Modifier.semantics { contentDescription = a11yStatus }
            )
            Spacer(Modifier.width(Sg.Space.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.shortName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    asset.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SgSignalBars(rssi = asset.rssi)
            Spacer(Modifier.width(Sg.Space.sm))
            SgPill(text = statusLabel(asset.status), color = color)
        }

        Spacer(Modifier.height(Sg.Space.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (asset.latitude != null) Icons.Default.LocationOn else Icons.Default.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(Sg.Space.xs))
            Text(
                if (asset.latitude != null && asset.longitude != null)
                    "%.4f, %.4f".format(asset.latitude, asset.longitude)
                else "Position unbekannt",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                relativeTime(asset.lastSeen?.time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        asset.batteryLevel?.let { level ->
            Spacer(Modifier.height(Sg.Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$level%",
                    style = MaterialTheme.typography.labelMedium,
                    color = batteryColor(level),
                    modifier = Modifier.width(38.dp)
                )
                SgMeter(
                    progress = level / 100f,
                    color = batteryColor(level),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (asset.maintenanceDue || onLocate != null || onAlarm != null) {
            Spacer(Modifier.height(Sg.Space.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
            ) {
                if (asset.maintenanceDue) {
                    SgPill(
                        text = "Wartung fällig",
                        color = statusColor(AssetStatus.MAINTENANCE),
                        icon = Icons.Default.Build
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onLocate != null) {
                    IconButton(onClick = onLocate) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Position anfordern",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onAlarm != null) {
                    IconButton(onClick = onAlarm) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = "Alarm auslösen",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
