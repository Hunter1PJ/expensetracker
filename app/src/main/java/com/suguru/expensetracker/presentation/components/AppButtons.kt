package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String = "primary_button"
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag),
        enabled = enabled,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        ),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.xxl,
            vertical = ExpenseTrackerSpacing.md
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(ExpenseTrackerTheme.iconSize.sm)
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(ExpenseTrackerSpacing.sm)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String = "secondary_button"
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag),
        enabled = enabled,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ExpenseTrackerTheme.extendedColors.surfaceHigh,
            contentColor = MaterialTheme.colorScheme.onBackground,
            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceLow,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
        ),
        border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.xxl,
            vertical = ExpenseTrackerSpacing.md
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(ExpenseTrackerTheme.iconSize.sm)
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(ExpenseTrackerSpacing.sm)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "tertiary_button"
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag),
        enabled = enabled,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
        ),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.md,
            vertical = ExpenseTrackerSpacing.sm
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String = "danger_button"
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag),
        enabled = enabled,
        shape = ExpenseTrackerRadius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = ExpenseTrackerTheme.extendedColors.financialDanger,
            contentColor = Color.White,
            disabledContainerColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
            disabledContentColor = ExpenseTrackerTheme.extendedColors.textMuted
        ),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.xxl,
            vertical = ExpenseTrackerSpacing.md
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(ExpenseTrackerTheme.iconSize.sm)
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.size(ExpenseTrackerSpacing.sm)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    containerColor: Color = ExpenseTrackerTheme.extendedColors.surfaceHigh,
    borderColor: Color = ExpenseTrackerTheme.extendedColors.borderSubtle,
    testTag: String = "app_icon_button"
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
        )
    }
}
