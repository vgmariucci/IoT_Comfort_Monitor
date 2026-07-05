package com.example.comfortplaces.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary        = Color(0xFFE94560),
    onPrimary      = Color.White,
    secondary      = Color(0xFFFFD93D),
    onSecondary    = Color.Black,
    tertiary       = Color(0xFF4CAF50),
    background     = Color(0xFF1A1A2E),
    onBackground   = Color(0xFFF8F8F8),
    surface        = Color(0xFF16213E),
    onSurface      = Color(0xFFF8F8F8),
    surfaceVariant = Color(0xFF0F3460),
    onSurfaceVariant = Color(0xFFB0B0C0),
    error          = Color(0xFFE94560),
    outline        = Color(0xFF1A5276),
)

private val LightColorScheme = lightColorScheme(
    primary        = Color(0xFFE94560),
    onPrimary      = Color.White,
    secondary      = Color(0xFFF97316),
    onSecondary    = Color.White,
    tertiary       = Color(0xFF4CAF50),
    background     = Color(0xFFF5F0E8),
    onBackground   = Color(0xFF1A1A2E),
    surface        = Color.White,
    onSurface      = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8E0D4),
    onSurfaceVariant = Color(0xFF505060),
    error          = Color(0xFFE94560),
    outline        = Color(0xFFD0C8BC),
)

@Composable
fun ComfortPlacesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}