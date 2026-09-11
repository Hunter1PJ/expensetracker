package com.example.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseTrackerGradients
import com.example.ui.theme.ExpenseTrackerTheme

enum class ProgressVariant {
    NORMAL,    // Purple gradient
    WARNING,   // Amber
    EXCEEDED   // Red
}

/**
 * Reusable Progress Bar with animated fill, smooth corners, and semantic coloring.
 */
@Composable
fun ExpenseTrackerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    variant: ProgressVariant = ProgressVariant.NORMAL,
    height: Dp = 8.dp,
    trackColor: Color = ExpenseTrackerTheme.extendedColors.surfaceHighlight
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress_bar_fill"
    )

    val shape = RoundedCornerShape(height / 2)

    val fillBrush = when (variant) {
        ProgressVariant.NORMAL -> ExpenseTrackerGradients.primaryHorizontal
        ProgressVariant.WARNING -> Brush.horizontalGradient(
            colors = listOf(
                ExpenseTrackerTheme.extendedColors.financialWarning,
                ExpenseTrackerTheme.extendedColors.financialWarning
            )
        )
        ProgressVariant.EXCEEDED -> ExpenseTrackerGradients.negativeGradient
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(shape)
                .background(fillBrush)
        )
    }
}
