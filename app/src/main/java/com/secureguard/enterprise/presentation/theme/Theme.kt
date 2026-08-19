package com.secureguard.enterprise.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0d47a1),
    secondary = Color(0xFF1976d2),
    tertiary = Color(0xFFff6f00),
    background = Color(0xFF1a1a2e),
    surface = Color(0xFF2d2d44)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0d47a1),
    secondary = Color(0xFF1976d2),
    tertiary = Color(0xFFff6f00),
    background = Color(0xFFf5f7fa),
    surface = Color(0xFFffffff)
)

@Composable
fun SecureGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
