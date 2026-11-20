package com.suguru.expensetracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Warm Paper Journal palette. Legacy property names are preserved so existing
// screens keep their APIs while receiving the new visual system.

val BackgroundDeepDark = Color(0xFF20231F)
val BackgroundAltDark = Color(0xFF272B25)
val SurfaceLowDark = Color(0xFF292D27)
val SurfaceDark = Color(0xFF30352E)
val SurfaceHighDark = Color(0xFF393F36)
val SurfaceHighlightDark = Color(0xFF454D42)

val PrimaryPurpleDark = Color(0xFF9AA88F)
val PrimaryBrightDark = Color(0xFFB4C0A9)
val PrimaryDeepDark = Color(0xFF718568)
val AccentIndigoDark = Color(0xFFB86B4F)
val AccentVioletDark = Color(0xFFD29A83)

val TextPrimaryDark = Color(0xFFF4F0E8)
val TextSecondaryDark = Color(0xFFC3BFB5)
val TextMutedDark = Color(0xFF96948B)

val BorderSubtleDark = Color(0xFF4A5046)
val DividerDark = Color(0xFF3D433A)

// Light Warm Paper Journal palette.
val BackgroundDeepLight = Color(0xFFFAF7F2)
val BackgroundAltLight = Color(0xFFF6F1E8)
val SurfaceLowLight = Color(0xFFFFFCF7)
val SurfaceLight = Color(0xFFFFFCF7)
val SurfaceHighLight = Color(0xFFF1ECE3)
val SurfaceHighlightLight = Color(0xFFE7EDE4)

val PrimaryPurpleLight = Color(0xFF2D5A43)
val PrimaryBrightLight = Color(0xFF52755D)
val PrimaryDeepLight = Color(0xFF214632)
val AccentIndigoLight = Color(0xFFB86B4F)
val AccentVioletLight = Color(0xFF98604C)

val TextPrimaryLight = Color(0xFF282923)
val TextSecondaryLight = Color(0xFF77766F)
val TextMutedLight = Color(0xFF96938B)

val BorderSubtleLight = Color(0xFFE5DED2)
val DividerLight = Color(0xFFEEE8DE)

// Financial semantic colors.
val SemanticPositiveDark = Color(0xFF9BB99A)
val SemanticPositiveContainerDark = Color(0xFF334A35)
val SemanticPositiveLight = Color(0xFF2D5A43)
val SemanticPositiveContainerLight = Color(0xFFE5EDE2)

val SemanticNegativeDark = Color(0xFFD99378)
val SemanticNegativeContainerDark = Color(0xFF52352C)
val SemanticNegativeLight = Color(0xFFB86B4F)
val SemanticNegativeContainerLight = Color(0xFFF3E5DE)

// Historical transfers are neutral information, not a creation action.
val SemanticTransferDark = Color(0xFFB9B5A9)
val SemanticTransferContainerDark = Color(0xFF42443D)
val SemanticTransferLight = Color(0xFF77766F)
val SemanticTransferContainerLight = Color(0xFFECE8DF)

val SemanticWarningDark = Color(0xFFD7B579)
val SemanticWarningContainerDark = Color(0xFF4A402D)
val SemanticWarningLight = Color(0xFF99723A)
val SemanticWarningContainerLight = Color(0xFFF4EBD8)

val SemanticDangerDark = Color(0xFFE28B79)
val SemanticDangerContainerDark = Color(0xFF57322D)
val SemanticDangerLight = Color(0xFFA94F40)
val SemanticDangerContainerLight = Color(0xFFF5E4DF)

// Kept for compatibility with components that still accept Brush tokens.
// The new design intentionally uses flat, paper-like surfaces without gradients.
object ExpenseTrackerGradients {
    val primary = Brush.linearGradient(listOf(PrimaryDeepLight, PrimaryDeepLight))
    val primaryHorizontal = Brush.horizontalGradient(listOf(PrimaryDeepLight, PrimaryDeepLight))
    val deepSurface = Brush.verticalGradient(listOf(SurfaceLowLight, SurfaceLowLight))
    val heroCard = Brush.linearGradient(listOf(SurfaceHighLight, SurfaceHighLight))
    val heroCardBorder = Brush.linearGradient(listOf(BorderSubtleLight, BorderSubtleLight))
    val heroGlow = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
    val positiveGradient = Brush.horizontalGradient(listOf(SemanticPositiveLight, SemanticPositiveLight))
    val negativeGradient = Brush.horizontalGradient(listOf(SemanticNegativeLight, SemanticNegativeLight))
}

object ExpenseTrackerGlow {
    val small = Color.Transparent
    val medium = Color.Transparent
    val large = Color.Transparent
    val violet = Color.Transparent
}

object ExpenseTrackerChartColors {
    val incomeSeries = SemanticPositiveLight
    val expenseSeries = SemanticNegativeLight
    val transferSeries = SemanticTransferLight
    val gridLine = Color(0xFFE5DED2)
    val axisLabel = TextSecondaryLight
    val chartGlow = Color.Transparent
    val chartSurface = SurfaceHighLight
}

@Immutable
data class ExtendedColors(
    val backgroundDeep: Color,
    val backgroundAlt: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighlight: Color,
    // Legacy names retained for source compatibility.
    val primaryPurple: Color,
    val primaryBright: Color,
    val primaryDeep: Color,
    val accentIndigo: Color,
    val accentViolet: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val divider: Color,
    val financialPositive: Color,
    val financialPositiveContainer: Color,
    val financialNegative: Color,
    val financialNegativeContainer: Color,
    val financialNeutral: Color,
    val financialNeutralContainer: Color,
    val financialWarning: Color,
    val financialWarningContainer: Color,
    val financialDanger: Color,
    val financialDangerContainer: Color,
    val surfaceElevated: Color = surfaceHigh,
    val cardBackground: Color = surface,
    val cardBorder: Color = borderSubtle,
    val textTertiary: Color = textMuted
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
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
}
