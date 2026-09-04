package com.g95nc.controller

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Design tokens for the G95NC controller.
 *
 * This is a signal-routing tool for one specific monitor (the Samsung Odyssey G9), used in short
 * bursts from the tray - so the palette leans into that monitor's own "Infinity Core" violet
 * rather than a generic dashboard purple, and a second, distinct hue (teal) is reserved for
 * "a source is actually live here", so it never gets confused with "this is selected".
 */
object AppColors {
    val background = Color(0xFF15131C)
    val surface = Color(0xFF1E1C29)
    val surfaceVariant = Color(0xFF2A2838)
    val outline = Color(0xFF3A3750)

    val accent = Color(0xFF9C7CFF)
    val accentMuted = Color(0xFF6B5B99)
    val signalActive = Color(0xFF52E0C4)
    val danger = Color(0xFFF2665F)

    val textPrimary = Color(0xFFF1EEFA)
    val textMuted = Color(0xFFA39DBF)
}

private val AppColorScheme = darkColorScheme(
    primary = AppColors.accent,
    onPrimary = Color(0xFF1B1030),
    primaryContainer = AppColors.accentMuted,
    onPrimaryContainer = AppColors.textPrimary,
    secondary = AppColors.signalActive,
    onSecondary = Color(0xFF042420),
    background = AppColors.background,
    onBackground = AppColors.textPrimary,
    surface = AppColors.surface,
    onSurface = AppColors.textPrimary,
    surfaceVariant = AppColors.surfaceVariant,
    onSurfaceVariant = AppColors.textMuted,
    outline = AppColors.outline,
    outlineVariant = AppColors.outline.copy(alpha = 0.6f),
    error = AppColors.danger,
    onError = Color(0xFF2A0A08)
)

// Machine-readable values (raw VCP/hex codes) get a monospace face so they read as data, distinct
// from the plain-sans labels around them - the only typographic distinction this app needs.
val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, typography = AppTypography, content = content)
}
