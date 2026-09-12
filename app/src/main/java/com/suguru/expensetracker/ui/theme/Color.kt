package com.suguru.expensetracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// NEW PREMIUM PURPLE/INDIGO COLOR PALETTE
// ==========================================

// Neutrals & Surfaces - Dark Palette
val BackgroundDeepDark = Color(0xFF09090F)      // Deep midnight canvas
val BackgroundAltDark = Color(0xFF0D0D16)       // Secondary background
val SurfaceLowDark = Color(0xFF11111B)          // Lowest elevated surface
val SurfaceDark = Color(0xFF161624)             // Default card / container surface
val SurfaceHighDark = Color(0xFF1C1C2D)         // Higher elevation / input fields
val SurfaceHighlightDark = Color(0xFF24243A)    // Selected state / active hover

// Brand Purple & Indigo Accents - Dark
val PrimaryPurpleDark = Color(0xFF8B5CF6)       // Primary brand purple
val PrimaryBrightDark = Color(0xFFA78BFA)       // Bright violet highlight
val PrimaryDeepDark = Color(0xFF6D4AFF)         // Rich deep purple/violet
val AccentIndigoDark = Color(0xFF6366F1)        // Indigo accent
val AccentVioletDark = Color(0xFFC084FC)        // Soft neon violet accent

// Typography - Dark
val TextPrimaryDark = Color(0xFFF8F7FF)         // High-contrast primary text
val TextSecondaryDark = Color(0xFFAAA6BE)       // Secondary / label text
val TextMutedDark = Color(0xFF747087)           // Muted captions / hints

// Borders & Dividers - Dark
val BorderSubtleDark = Color(0xFF2B2940)        // Subtle card borders
val DividerDark = Color(0xFF242238)             // Section dividers

// ------------------------------------------
// Neutrals & Surfaces - Light Palette
// ------------------------------------------
val BackgroundDeepLight = Color(0xFFF8F7FC)     // Soft lavender-white canvas
val BackgroundAltLight = Color(0xFFF2F1F8)      // Subtle off-white background
val SurfaceLowLight = Color(0xFFFFFFFF)         // White surface
val SurfaceLight = Color(0xFFFFFFFF)            // Default card background
val SurfaceHighLight = Color(0xFFF3F1F9)        // Elevated tinted surface
val SurfaceHighlightLight = Color(0xFFE8E5F5)   // Selected state / hover

// Brand Purple & Indigo Accents - Light
val PrimaryPurpleLight = Color(0xFF7C3AED)      // Vibrant purple
val PrimaryBrightLight = Color(0xFF8B5CF6)      // Mid purple
val PrimaryDeepLight = Color(0xFF6D28D9)        // Deep violet
val AccentIndigoLight = Color(0xFF4F46E5)       // Indigo accent
val AccentVioletLight = Color(0xFF9333EA)       // Violet accent

// Typography - Light
val TextPrimaryLight = Color(0xFF13111E)        // High-contrast dark text
val TextSecondaryLight = Color(0xFF5B5772)      // Secondary / label text
val TextMutedLight = Color(0xFF8B87A0)          // Muted captions

// Borders & Dividers - Light
val BorderSubtleLight = Color(0xFFE3E0EE)       // Subtle card borders
val DividerLight = Color(0xFFECE9F4)            // Section dividers

// ==========================================
// FINANCIAL SEMANTIC COLORS (Distinct from Brand)
// ==========================================
// Positive / Income (Soft Emerald)
val SemanticPositiveDark = Color(0xFF34D399)
val SemanticPositiveContainerDark = Color(0xFF0F382A)
val SemanticPositiveLight = Color(0xFF059669)
val SemanticPositiveContainerLight = Color(0xFFD1FAE5)

// Negative / Expense (Soft Coral / Red)
val SemanticNegativeDark = Color(0xFFF87171)
val SemanticNegativeContainerDark = Color(0xFF3B1818)
val SemanticNegativeLight = Color(0xFFDC2626)
val SemanticNegativeContainerLight = Color(0xFFFEE2E2)

// Transfer / Neutral (Indigo)
val SemanticTransferDark = Color(0xFF818CF8)
val SemanticTransferContainerDark = Color(0xFF1E1B4B)
val SemanticTransferLight = Color(0xFF4F46E5)
val SemanticTransferContainerLight = Color(0xFFE0E7FF)

// Warning / Approaching Limit (Amber)
val SemanticWarningDark = Color(0xFFFBBF24)
val SemanticWarningContainerDark = Color(0xFF3B260A)
val SemanticWarningLight = Color(0xFFD97706)
val SemanticWarningContainerLight = Color(0xFFFEF3C7)

// Danger / Exceeded (Red)
val SemanticDangerDark = Color(0xFFEF4444)
val SemanticDangerContainerDark = Color(0xFF450A0A)
val SemanticDangerLight = Color(0xFFDC2626)
val SemanticDangerContainerLight = Color(0xFFFEE2E2)

// ==========================================
// REUSABLE GRADIENTS & GLOWS
// ==========================================
object ExpenseTrackerGradients {
    val primary = Brush.linearGradient(
        colors = listOf(PrimaryDeepDark, PrimaryPurpleDark, PrimaryBrightDark)
    )

    val primaryHorizontal = Brush.horizontalGradient(
        colors = listOf(PrimaryDeepDark, PrimaryPurpleDark)
    )

    val deepSurface = Brush.verticalGradient(
        colors = listOf(Color(0xFF181426), SurfaceLowDark)
    )

    val heroCard = Brush.linearGradient(
        colors = listOf(Color(0xFF1F1A36), Color(0xFF141324), SurfaceLowDark)
    )

    val heroCardBorder = Brush.linearGradient(
        colors = listOf(Color(0x66A78BFA), Color(0x228B5CF6), Color(0x102B2940))
    )

    val heroGlow = Brush.radialGradient(
        colors = listOf(Color(0x338B5CF6), Color(0x008B5CF6))
    )

    val positiveGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF059669), Color(0xFF34D399))
    )

    val negativeGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFDC2626), Color(0xFFF87171))
    )
}

object ExpenseTrackerGlow {
    val small = Color(0x268B5CF6)   // 15% opacity
    val medium = Color(0x408B5CF6)  // 25% opacity
    val large = Color(0x668B5CF6)   // 40% opacity
    val violet = Color(0x33C084FC)  // 20% opacity
}

object ExpenseTrackerChartColors {
    val incomeSeries = SemanticPositiveDark
    val expenseSeries = SemanticNegativeDark
    val transferSeries = SemanticTransferDark
    val gridLine = Color(0x1FAAA6BE)
    val axisLabel = TextMutedDark
    val chartGlow = Color(0x338B5CF6)
    val chartSurface = SurfaceDark
}

// ==========================================
// EXTENDED COLOR SYSTEM
// ==========================================
@Immutable
data class ExtendedColors(
    val backgroundDeep: Color,
    val backgroundAlt: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighlight: Color,
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

    // Financial Semantics
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

    // Backward-compatible mappings
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
