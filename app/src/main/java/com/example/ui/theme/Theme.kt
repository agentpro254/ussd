package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

enum class DisplayScale(
    val title: String,
    val subtitle: String,
    val scaleFactor: Float,
    val icon: String
) {
    SMALL("Small", "Compact & dense view", 0.88f, "🔍"),
    STANDARD("Standard", "Default balanced layout", 1.0f, "📱"),
    LARGE("Large", "Big buttons & easy-read text", 1.18f, "🔎")
}

fun getDarkColorScheme(themeColor: AppThemeColor): androidx.compose.material3.ColorScheme {
    return darkColorScheme(
        primary = themeColor.accent,
        onPrimary = SlateDark,
        primaryContainer = themeColor.primaryDark,
        onPrimaryContainer = themeColor.container,
        secondary = themeColor.accent,
        onSecondary = Color.White,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        onBackground = DarkTextPrimary,
        onSurface = DarkTextPrimary,
        onSurfaceVariant = DarkTextSecondary,
        outline = DarkBorder,
        error = RoseError
    )
}

fun getLightColorScheme(themeColor: AppThemeColor): androidx.compose.material3.ColorScheme {
    return lightColorScheme(
        primary = themeColor.primary,
        onPrimary = Color.White,
        primaryContainer = themeColor.container,
        onPrimaryContainer = themeColor.onContainer,
        secondary = SlateMedium,
        onSecondary = Color.White,
        background = SlateBackground,
        surface = SlateSurface,
        surfaceVariant = Color(0xFFF1F5F9),
        onBackground = SlateDark,
        onSurface = SlateDark,
        onSurfaceVariant = SlateLight,
        outline = SlateBorder,
        error = RoseError
    )
}

@Composable
fun CodeeTheme(
    themeColor: AppThemeColor = AppThemeColor.TEAL,
    displayScale: DisplayScale = DisplayScale.STANDARD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> getDarkColorScheme(themeColor)
        else -> getLightColorScheme(themeColor)
    }

    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density * displayScale.scaleFactor,
        fontScale = currentDensity.fontScale * displayScale.scaleFactor
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
