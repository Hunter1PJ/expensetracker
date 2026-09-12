package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Ambient background glow effect placing a subtle, non-intrusive purple radiant orb
 * at the top of the canvas to create depth without distracting from data.
 */
@Composable
fun BackgroundGlowDecoration(
    modifier: Modifier = Modifier,
    glowColor: Color = ExpenseTrackerTheme.extendedColors.primaryPurple,
    alpha: Float = 0.14f
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val center = Offset(size.width * 0.85f, size.height * 0.05f)
        val radius = size.width * 0.75f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = alpha),
                    glowColor.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
}
