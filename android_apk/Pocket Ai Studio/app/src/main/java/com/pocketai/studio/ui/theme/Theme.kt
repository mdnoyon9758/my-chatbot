package com.pocketai.studio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Facebook-inspired palette
val FacebookBlue = Color(0xFF1877F2)
val FacebookBlueDark = Color(0xFF166fe5)
val FacebookBlueLight = Color(0xFF4A9BFF)

// Light theme
val LightBackground = Color(0xFFF0F2F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSecondaryText = Color(0xFF65676B)
val LightDivider = Color(0xFFE4E6EB)

// Dark theme
val DarkBackground = Color(0xFF18191A)
val DarkSurface = Color(0xFF242526)
val DarkSecondaryText = Color(0xFFB0B3B8)
val DarkDivider = Color(0xFF3E4042)

private val LightColorScheme = lightColorScheme(
    primary = FacebookBlue,
    onPrimary = Color.White,
    primaryContainer = FacebookBlueLight.copy(alpha = 0.2f),
    secondary = FacebookBlueDark,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurface,
    onBackground = Color(0xFF1C1E21),
    onSurface = Color(0xFF1C1E21),
    secondaryContainer = LightDivider,
    onSecondaryContainer = LightSecondaryText,
    outline = LightDivider
)

private val DarkColorScheme = darkColorScheme(
    primary = FacebookBlue,
    onPrimary = Color.White,
    primaryContainer = FacebookBlue.copy(alpha = 0.2f),
    secondary = FacebookBlueLight,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurface,
    onBackground = Color(0xFFE4E6EB),
    onSurface = Color(0xFFE4E6EB),
    secondaryContainer = DarkDivider,
    onSecondaryContainer = DarkSecondaryText,
    outline = DarkDivider
)

@Composable
fun PocketAiStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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
        typography = Typography(),
        content = content
    )
}