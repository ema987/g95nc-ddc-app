package com.g95nc.controller

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Light counterpart to [AppColors]. Keeps the same accent/signal hues (deepened slightly for
 * contrast on a light ground) so the app reads as the same product in either appearance. */
object AppLightColors {
    val background = Color(0xFFF7F6FB)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFE9E6F3)
    val outline = Color(0xFFCDC8DE)

    val accent = Color(0xFF6E4FD1)
    val accentMuted = Color(0xFFDCD3F5)
    val signalActive = Color(0xFF0E9480)
    val danger = Color(0xFFC22E27)

    val textPrimary = Color(0xFF211E2E)
    val textMuted = Color(0xFF5C5773)
}

private val AppLightColorScheme = lightColorScheme(
    primary = AppLightColors.accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = AppLightColors.accentMuted,
    onPrimaryContainer = Color(0xFF2A1B5E),
    secondary = AppLightColors.signalActive,
    onSecondary = Color(0xFFFFFFFF),
    background = AppLightColors.background,
    onBackground = AppLightColors.textPrimary,
    surface = AppLightColors.surface,
    onSurface = AppLightColors.textPrimary,
    surfaceVariant = AppLightColors.surfaceVariant,
    onSurfaceVariant = AppLightColors.textMuted,
    outline = AppLightColors.outline,
    outlineVariant = AppLightColors.outline.copy(alpha = 0.6f),
    error = AppLightColors.danger,
    onError = Color(0xFFFFFFFF)
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
fun AppTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    // Defaults to dark (this app's original look) while SYSTEM detection resolves, then swaps to
    // light if the OS turns out to be. DARK/LIGHT skip detection entirely - no flash, no I/O.
    var isDark by remember(themeMode) { mutableStateOf(themeMode != ThemeMode.LIGHT) }

    if (themeMode == ThemeMode.SYSTEM) {
        LaunchedEffect(Unit) {
            isDark = SystemTheme.isDark()
        }
    }

    val scheme = if (isDark) AppColorScheme else AppLightColorScheme
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
