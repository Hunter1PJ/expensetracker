package com.suguru.expensetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurpleDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3B2D6B),
    onPrimaryContainer = PrimaryBrightDark,
    secondary = AccentIndigoDark,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SurfaceHighlightDark,
    onSecondaryContainer = PrimaryBrightDark,
    tertiary = AccentVioletDark,
    onTertiary = Color(0xFFFFFFFF),
    background = BackgroundDeepDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark,
    outlineVariant = DividerDark,
    error = SemanticNegativeDark,
    onError = Color(0xFFFFFFFF),
    errorContainer = SemanticNegativeContainerDark,
    onErrorContainer = SemanticNegativeDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurpleLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = PrimaryDeepLight,
    secondary = AccentIndigoLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SurfaceHighlightLight,
    onSecondaryContainer = PrimaryPurpleLight,
    tertiary = AccentVioletLight,
    onTertiary = Color(0xFFFFFFFF),
    background = BackgroundDeepLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceHighLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight,
    outlineVariant = DividerLight,
    error = SemanticNegativeLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = SemanticNegativeContainerLight,
    onErrorContainer = SemanticNegativeLight
)

private val DarkExtendedColors = ExtendedColors(
    backgroundDeep = BackgroundDeepDark,
    backgroundAlt = BackgroundAltDark,
    surfaceLow = SurfaceLowDark,
    surface = SurfaceDark,
    surfaceHigh = SurfaceHighDark,
    surfaceHighlight = SurfaceHighlightDark,
    primaryPurple = PrimaryPurpleDark,
    primaryBright = PrimaryBrightDark,
    primaryDeep = PrimaryDeepDark,
    accentIndigo = AccentIndigoDark,
    accentViolet = AccentVioletDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    borderSubtle = BorderSubtleDark,
    divider = DividerDark,
    financialPositive = SemanticPositiveDark,
    financialPositiveContainer = SemanticPositiveContainerDark,
    financialNegative = SemanticNegativeDark,
    financialNegativeContainer = SemanticNegativeContainerDark,
    financialNeutral = SemanticTransferDark,
    financialNeutralContainer = SemanticTransferContainerDark,
    financialWarning = SemanticWarningDark,
    financialWarningContainer = SemanticWarningContainerDark,
    financialDanger = SemanticDangerDark,
    financialDangerContainer = SemanticDangerContainerDark,
    surfaceElevated = SurfaceHighDark,
    cardBackground = SurfaceDark,
    cardBorder = BorderSubtleDark,
    textTertiary = TextMutedDark
)

private val LightExtendedColors = ExtendedColors(
    backgroundDeep = BackgroundDeepLight,
    backgroundAlt = BackgroundAltLight,
    surfaceLow = SurfaceLowLight,
    surface = SurfaceLight,
    surfaceHigh = SurfaceHighLight,
    surfaceHighlight = SurfaceHighlightLight,
    primaryPurple = PrimaryPurpleLight,
    primaryBright = PrimaryBrightLight,
    primaryDeep = PrimaryDeepLight,
    accentIndigo = AccentIndigoLight,
    accentViolet = AccentVioletLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    borderSubtle = BorderSubtleLight,
    divider = DividerLight,
    financialPositive = SemanticPositiveLight,
    financialPositiveContainer = SemanticPositiveContainerLight,
    financialNegative = SemanticNegativeLight,
    financialNegativeContainer = SemanticNegativeContainerLight,
    financialNeutral = SemanticTransferLight,
    financialNeutralContainer = SemanticTransferContainerLight,
    financialWarning = SemanticWarningLight,
    financialWarningContainer = SemanticWarningContainerLight,
    financialDanger = SemanticDangerLight,
    financialDangerContainer = SemanticDangerContainerLight,
    surfaceElevated = SurfaceHighLight,
    cardBackground = SurfaceLight,
    cardBorder = BorderSubtleLight,
    textTertiary = TextMutedLight
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false by default to preserve the refined financial branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Accessor for extended design tokens in Composables:
 * ExpenseTrackerTheme.extendedColors.financialPositive
 */
object ExpenseTrackerTheme {
    val extendedColors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val spacing: ExpenseTrackerSpacing
        get() = ExpenseTrackerSpacing

    val radius: ExpenseTrackerRadius
        get() = ExpenseTrackerRadius

    val iconSize: ExpenseTrackerIconSize
        get() = ExpenseTrackerIconSize

    val elevation: ExpenseTrackerElevation
        get() = ExpenseTrackerElevation
}

// Backward compatibility alias for tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ExpenseTrackerTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
