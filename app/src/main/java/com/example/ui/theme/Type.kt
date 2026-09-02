package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val defaultPlatformTextStyle = PlatformTextStyle(
    includeFontPadding = false
)

private val defaultLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

// Complete, polished Material 3 typography hierarchy with financial clarity
val Typography = Typography(
    // Large financial hero numbers / balance
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),

    // Screen and section headers
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),

    // Titles & Card headers
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),

    // Body content
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),

    // Button labels, badges & metadata
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        platformStyle = defaultPlatformTextStyle,
        lineHeightStyle = defaultLineHeightStyle
    )
)
