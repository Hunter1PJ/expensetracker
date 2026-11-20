package com.suguru.expensetracker.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Compatibility wrapper retained for existing screen call sites.
 * Warm Paper Journal uses a calm, flat parchment canvas rather than ambient
 * glow effects. The modifier remains accepted to avoid changing screen APIs.
 */
@Composable
fun BackgroundGlowDecoration(
    modifier: Modifier = Modifier,
    glowColor: Color = Color.Transparent,
    alpha: Float = 0f
) {
    // Intentionally empty: the new visual system has no glow or gradient layer.
}
