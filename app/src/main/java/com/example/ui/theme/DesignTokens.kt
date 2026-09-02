package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design Tokens for ExpenseTracker.
 * Centralized dimensions, spacing, radii, and icon sizes.
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
    val huge: Dp = 48.dp

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
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val full: Dp = 999.dp

    val card: RoundedCornerShape = RoundedCornerShape(lg)
    val button: RoundedCornerShape = RoundedCornerShape(md)
    val chip: RoundedCornerShape = RoundedCornerShape(sm)
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = xl, topEnd = xl)
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
