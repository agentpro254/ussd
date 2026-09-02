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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
