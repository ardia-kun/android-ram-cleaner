package com.kidz.cleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF10B981)
private val GreenDark = Color(0xFF047857)
private val Bg = Color(0xFF0B1220)
private val BgLight = Color(0xFFF5F7FA)

private val DarkColors = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF04140D),
    primaryContainer = GreenDark,
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF60A5FA),
    background = Bg,
    surface = Color(0xFF111C2E),
    surfaceVariant = Color(0xFF1B2740),
    onBackground = Color(0xFFE6EDF6),
    onSurface = Color(0xFFE6EDF6),
)

private val LightColors = lightColorScheme(
    primary = GreenDark,
    secondary = Color(0xFF2563EB),
    background = BgLight,
    surface = Color(0xFFFFFFFF),
)

@Composable
fun RamCleanerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
