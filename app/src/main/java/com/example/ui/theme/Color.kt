package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Codee Official Brand Colors (#00B341 Green & #1A73E8 Blue)
val CodeeBrandGreen = Color(0xFF00B341)
val CodeeBrandGreenDark = Color(0xFF008731)
val CodeeBrandGreenLight = Color(0xFF33C267)
val CodeeBrandGreenContainer = Color(0xFFE6F8ED)
val OnCodeeBrandGreenContainer = Color(0xFF004D1B)

val CodeeBrandBlue = Color(0xFF1A73E8)
val CodeeBrandBlueDark = Color(0xFF1557B0)
val CodeeBrandBlueLight = Color(0xFF4285F4)
val CodeeBrandBlueContainer = Color(0xFFE8F0FE)
val OnCodeeBrandBlueContainer = Color(0xFF0D47A1)

// Semantic and Neutral Colors
val SlateDark = Color(0xFF0F172A)
val SlateMedium = Color(0xFF334155)
val SlateLight = Color(0xFF64748B)
val SlateBorder = Color(0xFFE2E8F0)
val SlateBackground = Color(0xFFF8FAFC)
val SlateSurface = Color(0xFFFFFFFF)

val EmeraldSuccess = Color(0xFF00B341)
val EmeraldSuccessBg = Color(0xFFE6F8ED)
val AmberWarning = Color(0xFFF59E0B)
val AmberWarningBg = Color(0xFFFFFBEB)
val RoseError = Color(0xFFEF4444)
val RoseErrorBg = Color(0xFFFEF2F2)
val IndigoInfo = Color(0xFF1A73E8)
val IndigoInfoBg = Color(0xFFE8F0FE)

// Backward-compatible alias
val TealPrimary = CodeeBrandGreen
val TealPrimaryDark = CodeeBrandGreenDark
val TealAccent = CodeeBrandGreenLight
val TealContainer = CodeeBrandGreenContainer
val OnTealContainer = OnCodeeBrandGreenContainer

// Dark Palette
val DarkBackground = Color(0xFF0D131A)
val DarkSurface = Color(0xFF161F2C)
val DarkSurfaceVariant = Color(0xFF202B3C)
val DarkBorder = Color(0xFF2D3B4E)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)

enum class AppThemeColor(
    val title: String,
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val previewColor: Color
) {
    TEAL(
        title = "Emerald Fintech",
        primary = CodeeBrandGreen,
        primaryDark = CodeeBrandGreenDark,
        accent = CodeeBrandGreenLight,
        container = CodeeBrandGreenContainer,
        onContainer = OnCodeeBrandGreenContainer,
        previewColor = CodeeBrandGreen
    ),
    BLUE(
        title = "Electric Blue",
        primary = CodeeBrandBlue,
        primaryDark = CodeeBrandBlueDark,
        accent = CodeeBrandBlueLight,
        container = CodeeBrandBlueContainer,
        onContainer = OnCodeeBrandBlueContainer,
        previewColor = CodeeBrandBlue
    ),
    INDIGO(
        title = "Deep Indigo",
        primary = Color(0xFF6366F1),
        primaryDark = Color(0xFF4F46E5),
        accent = Color(0xFF818CF8),
        container = Color(0xFFEEF2FF),
        onContainer = Color(0xFF312E81),
        previewColor = Color(0xFF6366F1)
    ),
    PURPLE(
        title = "Royal Purple",
        primary = Color(0xFF9333EA),
        primaryDark = Color(0xFF7E22CE),
        accent = Color(0xFFA855F7),
        container = Color(0xFFFAF5FF),
        onContainer = Color(0xFF581C87),
        previewColor = Color(0xFF9333EA)
    ),
    AMBER(
        title = "Warm Amber",
        primary = Color(0xFFD97706),
        primaryDark = Color(0xFFB45309),
        accent = Color(0xFFF59E0B),
        container = Color(0xFFFFFBEB),
        onContainer = Color(0xFF78350F),
        previewColor = Color(0xFFD97706)
    )
}

