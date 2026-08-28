package com.teamshryne.wediyo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = DarkSurfaceVariant,
    secondary = Color(0xFFAAAAAA),
    secondaryContainer = DarkSurfaceVariant,
    tertiary = RedPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = Color(0xFF2A2A2A),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF303030),
    outlineVariant = Color(0xFF272727),
    error = Color(0xFFFF3B30)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F0F0F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F0F0F),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF606060),
    secondaryContainer = LightSurfaceVariant,
    tertiary = RedPrimary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    surfaceContainer = Color(0xFFF8F8F8),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    onBackground = Color(0xFF0F0F0F),
    onSurface = Color(0xFF0F0F0F),
    onSurfaceVariant = Color(0xFF606060),
    outline = LightOutline,
    outlineVariant = Color(0xFFF1F1F1),
    error = Color(0xFFE00000)
)

@Composable
fun WediyoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
