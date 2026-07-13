package com.pocketai.studio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Accent palette (used by quick actions & highlights) ──
val AccentPink = Color(0xFFE91E63)
val AccentPurple = Color(0xFF9C27B0)
val AccentCyan = Color(0xFF00BCD4)
val AccentGreen = Color(0xFF4CAF50)
val AccentOrange = Color(0xFFFF9800)

// ── Light theme tokens ──
private val LightPrimary = Color(0xFF1A73E8)
private val LightOnPrimary = Color.White
private val LightPrimaryContainer = Color(0xFFD3E3FD)
private val LightOnPrimaryContainer = Color(0xFF041E49)
private val LightSecondary = Color(0xFF5F6368)
private val LightOnSecondary = Color.White
private val LightSecondaryContainer = Color(0xFFE8EAED)
private val LightOnSecondaryContainer = Color(0xFF1F1F1F)
private val LightTertiary = Color(0xFF1A8E5E)
private val LightOnTertiary = Color.White
private val LightTertiaryContainer = Color(0xFFC4EEDB)
private val LightOnTertiaryContainer = Color(0xFF002113)
private val LightBackground = Color(0xFFF8F9FA)
private val LightOnBackground = Color(0xFF1F1F1F)
private val LightSurface = Color.White
private val LightOnSurface = Color(0xFF1F1F1F)
private val LightSurfaceVariant = Color(0xFFF1F3F4)
private val LightOnSurfaceVariant = Color(0xFF5F6368)
private val LightError = Color(0xFFD93025)
private val LightOnError = Color.White
private val LightOutline = Color(0xFFDADCE0)
private val LightInverseSurface = Color(0xFF313235)
private val LightInverseOnSurface = Color(0xFFF1F3F4)

// ── Dark theme tokens ──
private val DarkPrimary = Color(0xFF8AB4F8)
private val DarkOnPrimary = Color(0xFF062E6F)
private val DarkPrimaryContainer = Color(0xFF1B4B91)
private val DarkOnPrimaryContainer = Color(0xFFD3E3FD)
private val DarkSecondary = Color(0xFF9AA0A6)
private val DarkOnSecondary = Color(0xFF3C4043)
private val DarkSecondaryContainer = Color(0xFF3C4043)
private val DarkOnSecondaryContainer = Color(0xFFE8EAED)
private val DarkTertiary = Color(0xFF81C995)
private val DarkOnTertiary = Color(0xFF003920)
private val DarkTertiaryContainer = Color(0xFF005232)
private val DarkOnTertiaryContainer = Color(0xFFC4EEDB)
private val DarkBackground = Color(0xFF111318)
private val DarkOnBackground = Color(0xFFE3E2E6)
private val DarkSurface = Color(0xFF1B1D22)
private val DarkOnSurface = Color(0xFFE3E2E6)
private val DarkSurfaceVariant = Color(0xFF252730)
private val DarkOnSurfaceVariant = Color(0xFFC4C7CF)
private val DarkError = Color(0xFFF28B82)
private val DarkOnError = Color(0xFF601410)
private val DarkOutline = Color(0xFF5F6368)
private val DarkInverseSurface = Color(0xFFE3E2E6)
private val DarkInverseOnSurface = Color(0xFF313235)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    onError = LightOnError,
    outline = LightOutline,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
    outline = DarkOutline,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface
)

// ── Typography hierarchy ──
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ── Shapes ──
val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PocketAiStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ dynamic color support
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
