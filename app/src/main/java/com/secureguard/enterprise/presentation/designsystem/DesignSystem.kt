package com.secureguard.enterprise.presentation.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.presentation.theme.AccentAmber
import com.secureguard.enterprise.presentation.theme.AccentCyan
import com.secureguard.enterprise.presentation.theme.AccentGreen
import com.secureguard.enterprise.presentation.theme.AccentRed
import com.secureguard.enterprise.presentation.theme.DeepNavy
import com.secureguard.enterprise.presentation.theme.OnSurfacePrimary
import com.secureguard.enterprise.presentation.theme.OnSurfaceSecondary
import com.secureguard.enterprise.presentation.theme.SurfaceCard
import com.secureguard.enterprise.presentation.theme.SurfaceDark

/**
 * SecureGuard Design System (§2).
 *
 * Tokens: Deep-Navy-Hintergrund, Cyan-Primary, Green-Healthy, Amber-Warning,
 * Red-Alarm, 8dp-Basisspacing, abgerundete Cards, dezente Elevation, hohe
 * Kontraste, Touch-Targets >= 48dp.
 *
 * Statusinformationen werden NIE ausschließlich über Farbe transportiert –
 * immer zusätzlich Text/Icon (§2).
 */
object SgTokens {
    val Spacing1 = 4.dp
    val Spacing2 = 8.dp
    val Spacing3 = 16.dp
    val Spacing4 = 24.dp
    val Spacing5 = 32.dp

    val CornerRadius: Dp = 14.dp
    val CardElevation: Dp = 2.dp
    val MinTouchTarget: Dp = 48.dp
}

/** Status-Farben, immer gepaart mit Text/Icon. */
enum class SgStatus(val color: Color, val label: String) {
    HEALTHY(AccentGreen, "healthy"),
    WARNING(AccentAmber, "warning"),
    ALARM(AccentRed, "alarm"),
    INFO(AccentCyan, "info"),
    UNKNOWN(OnSurfaceSecondary, "unknown")
}

/** 1:1-Umrandung, die zusätzlich einen Text-/Icon-Kanal für den Status trägt. */
@Composable
fun SgStatusBadge(
    status: SgStatus,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .semantics { contentDescription = "Status: ${status.label}" }
            .background(status.color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(status.color, RoundedCornerShape(50))
        )
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = status.color, modifier = Modifier.size(13.dp))
        }
        Text(
            status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = status.color,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Standard-Card für den SecureGuard-Look. */
@Composable
fun SgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(SgTokens.CornerRadius),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = SgTokens.CardElevation),
        border = BorderStroke(1.dp, Color(0xFF1D3A5C)),
        content = content
    )
}

/**
 * Kennzahl-Kachel (Metrics) mit optionalem Trend-Sparkline. Der Status wird
 * zusätzlich zum Farbwert als Icon/Text ausgegeben (nie nur Farbe).
 */
@Composable
fun SgMetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    status: SgStatus = SgStatus.INFO,
    sparkline: List<Float>? = null,
    modifier: Modifier = Modifier
) {
    SgCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = status.color,
                    fontWeight = FontWeight.Bold
                )
                Icon(icon, contentDescription = null, tint = status.color, modifier = Modifier.size(20.dp))
            }
            if (sparkline != null && sparkline.size > 1) {
                Spacer(Modifier.height(4.dp))
                SgSparkline(values = sparkline, color = status.color, modifier = Modifier.fillMaxWidth().height(22.dp))
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SgStatusBadge(status = status)
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Navigations-/Aktions-Kachel (Quick Tile), Touch-Target >= 48dp. */
@Composable
fun SgQuickTile(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    onClick: () -> Unit,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    SgCard(onClick = onClick, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(min = 64.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                if (badge != null) {
                    Box(
                        Modifier
                            .background(AccentRed, RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Mini-Liniendiagramm (Sparkline) auf Canvas. */
@Composable
fun SgSparkline(
    values: List<Float>,
    color: Color = AccentCyan,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val min = values.minOrNull() ?: 0f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val points = values.mapIndexed { i, v ->
            Offset(
                x = i * step,
                y = size.height - ((v - min) / range) * size.height
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

/**
 * Signalstärke-Balken. Die Anzahl der Balken wird zusätzlich als
 * contentDescription gesprochen (nie nur visuell).
 */
@Composable
fun SgSignalBars(
    strength: Int, // 0..5
    color: Color = AccentCyan,
    modifier: Modifier = Modifier
) {
    val bars = strength.coerceIn(0, 5)
    val desc = when {
        bars == 0 -> "Kein Signal"
        bars <= 2 -> "Schwaches Signal"
        bars <= 4 -> "Mittleres Signal"
        else -> "Starkes Signal"
    }
    Row(
        modifier = modifier.semantics { contentDescription = desc },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until 5) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .background(
                        if (i < bars) color else OnSurfaceSecondary.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/** Abschnitts-Header mit optionaler Trailing-Action. */
@Composable
fun SgSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurfacePrimary
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = AccentCyan) }
        }
    }
}

/** Primär-Button (Cyan-Fill, min. 48dp Höhe). */
@Composable
fun SgPrimaryButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(SgTokens.CornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentCyan,
            contentColor = DeepNavy,
            disabledContainerColor = OnSurfaceSecondary.copy(alpha = 0.4f),
            disabledContentColor = Color.White
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Sekundär-Button (Outline). */
@Composable
fun SgSecondaryButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(SgTokens.CornerRadius),
        border = BorderStroke(1.dp, AccentCyan),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Icon-Button mit 48dp-Touch-Target. */
@Composable
fun SgIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AccentCyan,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** Leer-Zustand mit Icon, Titel, Text und optionaler Aktion. */
@Composable
fun SgEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SgTokens.Spacing4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = OnSurfaceSecondary, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            SgPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Lade-Zustand (Spinner + optionaler Text). */
@Composable
fun SgLoadingState(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(color = AccentCyan)
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Mehraufwand-Fortschritt (z. B. Channel-Auslastung). */
@Composable
fun SgProgressBar(
    progress: Float,
    color: Color = AccentCyan,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier,
        color = color,
        trackColor = SurfaceDark.copy(alpha = 0.6f),
        strokeCap = StrokeCap.Round
    )
}

/**
 * Bestätigungs-Dialog (kritische Aktionen) mit roter Primäraktion. Status wird
 * über Text vermittelt, nicht nur über Farbe.
 */
@Composable
fun SgConfirmDialog(
    show: MutableState<Boolean>,
    title: String,
    message: String,
    confirmLabel: String = "Bestätigen",
    dismissLabel: String = "Abbrechen",
    onConfirm: () -> Unit
) {
    if (show.value) {
        AlertDialog(
            onDismissRequest = { show.value = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        show.value = false
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = Color.White)
                ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { show.value = false }) { Text(dismissLabel) }
            }
        )
    }
}

/** Farbverlauf für Hero-/Header-Flächen. */
@Composable
fun sgHeroBrush(): Brush = Brush.linearGradient(
    listOf(
        Color(0xFF0A2540),
        Color(0xFF061A2E)
    )
)
