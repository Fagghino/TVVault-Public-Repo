package com.fagghino.tvvault.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = AccentTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = BackgroundDark,
    onSecondary = BackgroundDark,
    onBackground = PrimaryDark,
    onSurface = PrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = AccentTeal,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = BackgroundLight,
    onSecondary = BackgroundLight,
    onBackground = PrimaryLight,
    onSurface = PrimaryLight
)

/**
 * @param themeMode "system" | "light" | "dark"
 */
@Composable
fun TVVaultTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    // Forcing Dark Theme by default for the "TV Time" aesthetic
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> true // Dark mode is the intended default for this media app aesthetic
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
