package com.davipeter.iana.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF4FD1FF),
    onPrimary = Color(0xFF00222E),
    primaryContainer = Color(0xFF0B3A4A),
    onPrimaryContainer = Color(0xFFBFEFFF),
    secondaryContainer = Color(0xFF1E2630),
    onSecondaryContainer = Color(0xFFDDE5EE),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE3E8EE),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFE3E8EE),
    surfaceVariant = Color(0xFF161D25),
    onSurfaceVariant = Color(0xFF9AA7B4),
    error = Color(0xFFFF8A80),
)

/** Tema escuro fixo (o design fica pra depois). */
@Composable
fun IanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
