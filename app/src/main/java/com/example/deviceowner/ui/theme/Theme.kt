package com.example.deviceowner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Official DEVICEOWNER Theme - White background, Black text, Blue buttons
private val DeviceOwnerColorScheme = lightColorScheme(
    // Primary colors - Blue
    primary = Color(0xFF1E88E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF42A5F5),
    onPrimaryContainer = Color(0xFFFFFFFF),

    // Secondary colors - Blue
    secondary = Color(0xFF42A5F5),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF000000),

    // Tertiary colors
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBDEFB),
    onTertiaryContainer = Color(0xFF000000),

    // Error colors
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF000000),

    // Background & Surface - WHITE
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242),

    // Borders & Outlines
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFF0F0F0),

    // Surface tints
    surfaceTint = Color(0xFF1E88E5),
    inverseSurface = Color(0xFF000000),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF42A5F5)
)

@Composable
fun DeviceOwnerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DeviceOwnerColorScheme,
        content = content
    )
}
