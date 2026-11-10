package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AtlasDarkColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = TextMain,
    primaryContainer = AccentPurple.copy(alpha = 0.25f),
    onPrimaryContainer = AccentPurpleLight,
    secondary = AccentEmerald,
    onSecondary = BgCanvas,
    secondaryContainer = AccentEmerald.copy(alpha = 0.2f),
    onSecondaryContainer = AccentEmeraldLight,
    tertiary = AccentCyan,
    onTertiary = BgCanvas,
    background = BgCanvas,
    onBackground = TextMain,
    surface = BgCard,
    onSurface = TextMain,
    surfaceVariant = BgInput,
    onSurfaceVariant = TextMuted,
    outline = BorderSubtle,
    outlineVariant = BorderActive,
    error = AccentRose,
    onError = TextMain
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to stunning dark cyber executive theme
    content: @Composable () -> Unit
) {
    val colorScheme = AtlasDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = BgCanvas.toArgb()
                window.navigationBarColor = BgCanvas.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
