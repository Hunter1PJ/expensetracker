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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = NeutralDark400,
    onSecondary = NeutralDark950,
    secondaryContainer = NeutralDark800,
    onSecondaryContainer = NeutralDark200,
    tertiary = NeutralBlueDark,
    onTertiary = NeutralDark950,
    background = NeutralDark950,
    onBackground = NeutralDark50,
    surface = NeutralDark900,
    onSurface = NeutralDark50,
    surfaceVariant = NeutralDark850,
    onSurfaceVariant = NeutralDark400,
    outline = NeutralDark700,
    outlineVariant = NeutralDark800,
    error = NegativeRedDark,
    onError = NeutralDark950,
    errorContainer = NegativeRedContainerDark,
    onErrorContainer = NegativeRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = NeutralLight600,
    onSecondary = NeutralLight100,
    secondaryContainer = NeutralLight200,
    onSecondaryContainer = NeutralLight800,
    tertiary = NeutralBlueLight,
    onTertiary = NeutralLight100,
    background = NeutralLight50,
    onBackground = NeutralLight950,
    surface = NeutralLight100,
    onSurface = NeutralLight950,
    surfaceVariant = NeutralLight200,
    onSurfaceVariant = NeutralLight600,
    outline = NeutralLight300,
    outlineVariant = NeutralLight200,
    error = NegativeRedLight,
    onError = NeutralLight100,
    errorContainer = NegativeRedContainerLight,
    onErrorContainer = NegativeRedLight
)

private val DarkExtendedColors = ExtendedColors(
    financialPositive = PositiveGreenDark,
    financialPositiveContainer = PositiveGreenContainerDark,
    financialNegative = NegativeRedDark,
    financialNegativeContainer = NegativeRedContainerDark,
    financialNeutral = NeutralBlueDark,
    surfaceElevated = NeutralDark850,
    surfaceHighlight = NeutralDark800,
    cardBackground = NeutralDark900,
    cardBorder = NeutralDark700,
    textPrimary = NeutralDark50,
    textSecondary = NeutralDark400,
    textTertiary = NeutralDark600
)

private val LightExtendedColors = ExtendedColors(
    financialPositive = PositiveGreenLight,
    financialPositiveContainer = PositiveGreenContainerLight,
    financialNegative = NegativeRedLight,
    financialNegativeContainer = NegativeRedContainerLight,
    financialNeutral = NeutralBlueLight,
    surfaceElevated = NeutralLight200,
    surfaceHighlight = NeutralLight300,
    cardBackground = NeutralLight100,
    cardBorder = NeutralLight300,
    textPrimary = NeutralLight950,
    textSecondary = NeutralLight600,
    textTertiary = NeutralLight400
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
