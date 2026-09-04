package com.secureguard.enterprise.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Stitch Design System: Dark Enterprise Theme (#0a2540 Deep Navy)
private val DarkEnterpriseScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = DeepNavy,
    primaryContainer = NavyLight,
    onPrimaryContainer = OnSurfacePrimary,
    secondary = AccentGreen,
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFF004D25),
    onSecondaryContainer = AccentGreen,
    tertiary = AccentAmber,
    onTertiary = DeepNavy,
    tertiaryContainer = Color(0xFF4D3800),
    onTertiaryContainer = AccentAmber,
    error = AccentRed,
    onError = Color.White,
    errorContainer = Color(0xFF4D0011),
    onErrorContainer = AccentRed,
    background = SurfaceDark,
    onBackground = OnSurfacePrimary,
    surface = SurfaceDark,
    onSurface = OnSurfacePrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = OnSurfaceSecondary,
    outline = OnSurfaceMuted,
    outlineVariant = Color(0xFF2A3A4A),
    inverseSurface = OnSurfacePrimary,
    inverseOnSurface = SurfaceDark,
)

private val LightEnterpriseScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = DeepNavy,
    secondary = Color(0xFF006B35),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9AF6B4),
    onSecondaryContainer = Color(0xFF00210C),
    tertiary = Color(0xFF6B5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8DE51),
    onTertiaryContainer = Color(0xFF211A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = DeepNavy,
    surface = Color(0xFFF8FAFC),
    onSurface = DeepNavy,
    surfaceVariant = Color(0xFFDFE3EB),
    onSurfaceVariant = Color(0xFF43556B),
    outline = Color(0xFF73859A),
)

@Composable
fun SecureGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Bewusst kein Material-You/Dynamic-Color: die Statusfarben des
    // Design-Systems (statusColor, severityColor, sourceColor) sind auf diese
    // Palette abgestimmt. Eine vom Nutzer gefaerbte Oberflaeche wuerde die
    // Bedeutung von Rot/Bernstein/Gruen im Lagebild verwaessern.
    val colorScheme = if (darkTheme) DarkEnterpriseScheme else LightEnterpriseScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (darkTheme) SurfaceDark else DeepNavy).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EnterpriseTypography,
        content = content
    )
}
