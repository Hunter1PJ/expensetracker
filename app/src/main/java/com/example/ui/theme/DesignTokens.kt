package com.example.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design Tokens for ExpenseTracker.
 * Centralized dimensions, spacing, radii, surface tokens, and animation curves.
 */
object ExpenseTrackerSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val x40: Dp = 40.dp
    val huge: Dp = 48.dp

    // Standardized numbered aliases
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space20: Dp = 20.dp
    val space24: Dp = 24.dp
    val space32: Dp = 32.dp
    val space40: Dp = 40.dp
    val space48: Dp = 48.dp

    // Screen padding convention
    val screenHorizontal: Dp = 20.dp
    val screenVertical: Dp = 16.dp
    val sectionSpacing: Dp = 24.dp
    val cardContentPadding: Dp = 20.dp
    val cardInnerSpacing: Dp = 12.dp
    val itemSpacing: Dp = 8.dp
}

object ExpenseTrackerRadius {
    val none: Dp = 0.dp
    val xs: Dp = 6.dp
    val small: Dp = 10.dp
    val sm: Dp = 10.dp
    val medium: Dp = 14.dp
    val md: Dp = 14.dp
    val large: Dp = 18.dp
    val lg: Dp = 18.dp
    val xlarge: Dp = 24.dp
    val xl: Dp = 24.dp
    val hero: Dp = 28.dp
    val pill: Dp = 999.dp
    val full: Dp = 999.dp

    val card: RoundedCornerShape = RoundedCornerShape(large)
    val cardHero: RoundedCornerShape = RoundedCornerShape(hero)
    val cardInteractive: RoundedCornerShape = RoundedCornerShape(large)
    val button: RoundedCornerShape = RoundedCornerShape(medium)
    val chip: RoundedCornerShape = RoundedCornerShape(small)
    val chipPill: RoundedCornerShape = RoundedCornerShape(pill)
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = hero, topEnd = hero)
    val searchBar: RoundedCornerShape = RoundedCornerShape(medium)
    val dialog: RoundedCornerShape = RoundedCornerShape(xlarge)
}

object ExpenseTrackerIconSize {
    val xs: Dp = 14.dp
    val sm: Dp = 18.dp
    val md: Dp = 22.dp
    val lg: Dp = 26.dp
    val xl: Dp = 32.dp
    val display: Dp = 48.dp
}

object ExpenseTrackerElevation {
    val flat: Dp = 0.dp
    val low: Dp = 1.dp
    val medium: Dp = 3.dp
    val high: Dp = 6.dp
}

object ExpenseTrackerSurfaces {
    const val BORDER_WIDTH_DP = 1f
    const val ACTIVE_BORDER_WIDTH_DP = 1.5f
    const val HERO_BORDER_WIDTH_DP = 1.25f
    const val CARD_ALPHA = 0.92f
    const val GLOW_ALPHA_SUBTLE = 0.12f
    const val GLOW_ALPHA_MEDIUM = 0.25f
}

object ExpenseTrackerAnimation {
    const val FAST_MS = 140
    const val NORMAL_MS = 240
    const val SLOW_MS = 360

    val easeEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val easeStandard: Easing = FastOutSlowInEasing
    val easeDecelerate: Easing = LinearOutSlowInEasing

    val fastTween = tween<Float>(durationMillis = FAST_MS, easing = easeStandard)
    val normalTween = tween<Float>(durationMillis = NORMAL_MS, easing = easeStandard)
    val slowTween = tween<Float>(durationMillis = SLOW_MS, easing = easeEmphasized)
}
