package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerTheme

enum class AvatarShape {
    CIRCLE,
    ROUNDED_SQUARE
}

/**
 * Reusable Icon Avatar with soft tinted background and optional subtle border.
 */
@Composable
fun IconAvatar(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = tint.copy(alpha = 0.16f),
    borderColor: Color? = tint.copy(alpha = 0.3f),
    shapeType: AvatarShape = AvatarShape.ROUNDED_SQUARE
) {
    val shape: Shape = when (shapeType) {
        AvatarShape.CIRCLE -> CircleShape
        AvatarShape.ROUNDED_SQUARE -> RoundedCornerShape(12.dp)
    }

    val borderModifier = if (borderColor != null) {
        modifier.border(1.dp, borderColor, shape)
    } else {
        modifier
    }

    Box(
        modifier = borderModifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}
