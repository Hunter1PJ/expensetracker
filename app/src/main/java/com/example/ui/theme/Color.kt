package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==========================================
// CORE COLOR PALETTE (Dark First & Light)
// ==========================================

// Neutrals - Dark Palette
val NeutralDark950 = Color(0xFF0D0F11) // Background
val NeutralDark900 = Color(0xFF15181C) // Surface Default (Card Background)
val NeutralDark850 = Color(0xFF1C2025) // Surface Elevated / Variant
val NeutralDark800 = Color(0xFF252A31) // Surface Highlight / Selected
val NeutralDark700 = Color(0xFF374151) // Borders / Dividers (Subtle)
val NeutralDark600 = Color(0xFF4B5563) // Borders (Active)
val NeutralDark400 = Color(0xFF9CA3AF) // Muted Secondary Text
val NeutralDark200 = Color(0xFFE5E7EB) // Body Text
val NeutralDark50 = Color(0xFFF9FAFB)  // Off-white Primary Text / Headings

// Neutrals - Light Palette
val NeutralLight50 = Color(0xFFF9FAFB)  // Background
val NeutralLight100 = Color(0xFFFFFFFF) // Surface Default (Card Background)
val NeutralLight200 = Color(0xFFF3F4F6) // Surface Elevated / Variant
val NeutralLight300 = Color(0xFFE5E7EB) // Borders / Dividers (Subtle)
val NeutralLight400 = Color(0xFFD1D5DB) // Borders (Active)
val NeutralLight600 = Color(0xFF4B5563) // Muted Secondary Text
val NeutralLight800 = Color(0xFF1F2937) // Body Text
val NeutralLight950 = Color(0xFF111827) // Primary Text / Headings

// Financial Accents - Emerald / Forest Green
val EmeraldPrimaryDark = Color(0xFF10B981) // Main Accent in Dark
val EmeraldOnPrimaryDark = Color(0xFF022C22)
val EmeraldContainerDark = Color(0xFF064E3B)
val EmeraldOnContainerDark = Color(0xFFA7F3D0)

val EmeraldPrimaryLight = Color(0xFF059669) // Main Accent in Light
val EmeraldOnPrimaryLight = Color(0xFFFFFFFF)
val EmeraldContainerLight = Color(0xFFD1FAE5)
val EmeraldOnContainerLight = Color(0xFF065F46)

// Financial Semantic Colors (Income / Expense / Neutral)
val PositiveGreenDark = Color(0xFF34D399)
val PositiveGreenLight = Color(0xFF16A34A)
val PositiveGreenContainerDark = Color(0xFF064E3B)
val PositiveGreenContainerLight = Color(0xFFDCFCE7)

val NegativeRedDark = Color(0xFFF87171)
val NegativeRedLight = Color(0xFFDC2626)
val NegativeRedContainerDark = Color(0xFF7F1D1D)
val NegativeRedContainerLight = Color(0xFFFEE2E2)

val NeutralBlueDark = Color(0xFF60A5FA)
val NeutralBlueLight = Color(0xFF2563EB)

/**
 * Extended Financial semantic colors for ExpenseTracker.
 */
@Immutable
data class ExtendedColors(
    val financialPositive: Color,
    val financialPositiveContainer: Color,
    val financialNegative: Color,
    val financialNegativeContainer: Color,
    val financialNeutral: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
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
}
