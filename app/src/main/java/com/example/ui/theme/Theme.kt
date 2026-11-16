package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = CrimsonEmergency,
    onPrimary = Color.White,
    primaryContainer = CrimsonLight,
    onPrimaryContainer = CrimsonDark,
    secondary = SafetyNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = SafetyNavy,
    tertiary = ResponderEmerald,
    onTertiary = Color.White,
    tertiaryContainer = ResponderEmeraldContainer,
    onTertiaryContainer = Color(0xFF064E3B),
    background = LightBackground,
    onBackground = SafetyNavy,
    surface = LightSurface,
    onSurface = SafetyNavy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SafetySlateMuted,
    outline = LightOutline,
    error = CrimsonEmergency,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B6B),
    onPrimary = Color(0xFF450A0A),
    primaryContainer = Color(0xFF7F1D1D),
    onPrimaryContainer = Color(0xFFFEE2E2),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = ResponderEmeraldDark,
    onTertiary = Color(0xFF064E3B),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = DarkBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

@Composable
fun RoadSafeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RoadSafeTypography,
        content = content,
    )
}
