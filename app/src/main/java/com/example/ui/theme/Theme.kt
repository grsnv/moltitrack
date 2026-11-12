package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioColorScheme = darkColorScheme(
    primary = StudioCyan,
    onPrimary = StudioDark,
    primaryContainer = StudioCardElevated,
    onPrimaryContainer = StudioTextPrimary,
    secondary = StudioGreen,
    onSecondary = StudioDark,
    tertiary = StudioRed,
    background = StudioDark,
    surface = StudioSurface,
    surfaceVariant = StudioCard,
    onBackground = StudioTextPrimary,
    onSurface = StudioTextPrimary,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder
)

@Composable
fun MultiTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = StudioDark.toArgb()
                window.navigationBarColor = StudioDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
