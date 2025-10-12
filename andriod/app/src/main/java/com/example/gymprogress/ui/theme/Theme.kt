package com.example.gymprogress.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreen.copy(alpha = 0.15f),
    onPrimaryContainer = PrimaryGreen,
    secondary = SecondaryGreen,
    onSecondary = Color.White,
    secondaryContainer = SecondaryGreen.copy(alpha = 0.16f),
    onSecondaryContainer = SecondaryGreen,
    background = Color.White,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = TextSecondary,
    outline = OutlineLight,
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = SecondaryGreen,
    onPrimary = Color.Black,
    secondary = PrimaryGreen,
    onSecondary = Color.Black,
    background = Color(0xFF111827),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFCBD5F5),
    outline = Color(0xFF4B5563)
)

@Composable
fun GymProgressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
