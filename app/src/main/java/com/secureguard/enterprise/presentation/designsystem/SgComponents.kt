package com.secureguard.enterprise.presentation.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/* ------------------------------------------------------------------ */
/* Flächen & Container                                                 */
/* ------------------------------------------------------------------ */

/**
 * Basis-Karte des Design-Systems: weiche Radien, dezenter Rahmen und
 * optionaler Akzent-Verlauf. Ersetzt die uneinheitlichen `Card`-Aufrufe.
 */
@Composable
fun SgCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Sg.Space.lg),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        targetValue = when {
            selected && accent != null -> accent
            selected -> scheme.primary
            accent != null -> accent.copy(alpha = 0.28f)
            else -> scheme.outlineVariant
        },
        animationSpec = tween(220),
        label = "cardBorder"
    )
    val base = scheme.surfaceVariant
    val brush = Brush.verticalGradient(
        listOf(
            if (accent != null) accent.copy(alpha = if (selected) 0.20f else 0.10f) else base,
            base
        )
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(Sg.Radius.lg))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        color = Color.Transparent,
        shape = RoundedCornerShape(Sg.Radius.lg),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .background(brush)
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Abschnittsüberschrift mit Icon und optionaler Aktion rechts.
 * Sorgt für eine klare visuelle Hierarchie auf allen Screens.
 */
@Composable
fun SgSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(Sg.Radius.sm))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Sg.Space.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/* ------------------------------------------------------------------ */
/* Status-Indikatoren                                                  */
/* ------------------------------------------------------------------ */

/** Statuspunkt; pulsiert bei `live = true` als "es passiert gerade etwas". */
@Composable
fun SgStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 10.dp
) {
    val transition = rememberInfiniteTransition(label = "statusDot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulse"
    )
    Box(modifier = modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        if (live) {
            Box(
                modifier = Modifier
                    .size(size * 2.2f)
                    .alpha(1f - pulse)
                    .background(color.copy(alpha = 0.35f), CircleShape)
            )
        }
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }
}

/** Kompakter Status-Chip mit Punkt/Icon – app-weit einheitlich. */
@Composable
fun SgPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    live: Boolean = false,
    filled: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Sg.Radius.pill))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(Sg.Radius.pill))
            .padding(horizontal = Sg.Space.md, vertical = Sg.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sg.Space.xs)
    ) {
        when {
            icon != null -> Icon(
                icon,
                contentDescription = null,
                tint = if (filled) MaterialTheme.colorScheme.surface else color,
                modifier = Modifier.size(14.dp)
            )
            live -> SgStatusDot(color = color, live = true, size = 7.dp)
            else -> Box(Modifier.size(7.dp).background(color, CircleShape))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
            maxLines = 1
        )
    }
}

/** Signalstärke als 4 Balken – ersetzt die reine dBm-Zahl. */
@Composable
fun SgSignalBars(
    rssi: Int,
    modifier: Modifier = Modifier,
    barWidth: androidx.compose.ui.unit.Dp = 3.dp,
    maxHeight: androidx.compose.ui.unit.Dp = 16.dp
) {
    val active = rssiBars(rssi)
    val color = rssiColor(rssi)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * (0.35f + 0.22f * (i - 1)))
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (i <= active) color
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
            )
        }
    }
}

/** Schlanker Fortschrittsbalken mit Farbverlauf (Akku, Auslastung, ...). */
@Composable
fun SgMeter(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 6.dp
) {
    val clamped = min(1f, max(0f, progress))
    val animated by animateFloatAsState(clamped, tween(500), label = "meter")
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Sg.Radius.pill))
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(Sg.Radius.pill))
                .background(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color))
                )
        )
    }
}

/** Kreisförmiger Fortschritt mit Wert in der Mitte (Agent-Zyklus u. a.). */
@Composable
fun SgProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    center: @Composable () -> Unit = {}
) {
    val clamped = min(1f, max(0f, progress))
    val animated by animateFloatAsState(clamped, tween(600), label = "ring")
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke
                )
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke,
                    size.height - stroke
                )
            )
        }
        center()
    }
}

/** Mini-Verlaufsgrafik (Sparkline) – zeigt Trends statt nur Momentwerte. */
@Composable
fun SgSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) {
        Box(modifier = modifier)
        return
    }
    val maxValue = values.maxOrNull() ?: 0f
    val minValue = values.minOrNull() ?: 0f
    val span = (maxValue - minValue).takeIf { it > 0.0001f } ?: 1f

    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(
                x = stepX * index,
                y = size.height - ((value - minValue) / span) * size.height * 0.9f - size.height * 0.05f
            )
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))
            )
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawCircle(color = color, radius = 3.dp.toPx(), center = points.last())
    }
}

/* ------------------------------------------------------------------ */
/* Kacheln                                                             */
/* ------------------------------------------------------------------ */

/**
 * Kennzahlen-Kachel mit Icon, Wert, Label, optionalem Trend und Sparkline.
 * Ersetzt die alte, rein statische `StatCard`.
 */
@Composable
fun SgMetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    trend: String? = null,
    trendUp: Boolean? = null,
    series: List<Float> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    SgCard(
        modifier = modifier,
        accent = color,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Sg.Radius.sm))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            if (trend != null) {
                val trendColor = when (trendUp) {
                    true -> statusColor(com.secureguard.enterprise.data.model.AssetStatus.ONLINE)
                    false -> statusColor(com.secureguard.enterprise.data.model.AssetStatus.OFFLINE)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    trend,
                    style = MaterialTheme.typography.labelSmall,
                    color = trendColor,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(Sg.Space.sm))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (series.size > 1) {
            Spacer(Modifier.height(Sg.Space.sm))
            SgSparkline(
                values = series,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }
    }
}

/** Quadratische Schnellzugriff-Kachel für Navigation und Werkzeuge. */
@Composable
fun SgQuickTile(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null
) {
    SgCard(
        modifier = modifier,
        accent = color,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Sg.Radius.sm))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.weight(1f))
            if (badge != null) {
                SgPill(text = badge, color = color)
            }
        }
        Spacer(Modifier.height(Sg.Space.sm))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Zustände                                                            */
/* ------------------------------------------------------------------ */

/** Aussagekräftiger Leerzustand statt kommentarlos leerer Liste. */
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
            .padding(Sg.Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Sg.Space.sm)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Skeleton-Platzhalter (Shimmer) für Ladephasen. */
@Composable
fun SgSkeleton(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Sg.Radius.sm))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    )
}

/** Bestätigungsdialog für sicherheitskritische Aktionen. */
@Composable
fun SgConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.error,
    icon: ImageVector? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = accent) }
        } else null,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        shape = RoundedCornerShape(Sg.Radius.lg)
    )
}
