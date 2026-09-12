package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Filter chip with purple-tinted active state and dark elevated inactive state.
 */
@Composable
fun ExpenseTrackerFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = ExpenseTrackerRadius.chip,
    testTag: String = "filter_chip"
) {
    val containerColor = if (selected) {
        ExpenseTrackerTheme.extendedColors.primaryPurple.copy(alpha = 0.22f)
    } else {
        ExpenseTrackerTheme.extendedColors.surfaceHigh
    }

    val borderColor = if (selected) {
        ExpenseTrackerTheme.extendedColors.primaryPurple
    } else {
        ExpenseTrackerTheme.extendedColors.borderSubtle
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.onBackground
    } else {
        ExpenseTrackerTheme.extendedColors.textSecondary
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = ExpenseTrackerSpacing.md, vertical = ExpenseTrackerSpacing.sm)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                )
                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

/**
 * Financial status chip (Active, Pending, Exceeded, Completed).
 */
@Composable
fun ExpenseTrackerStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    shape: Shape = ExpenseTrackerRadius.chipPill,
    testTag: String = "status_chip"
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = ExpenseTrackerSpacing.sm, vertical = ExpenseTrackerSpacing.xxs)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

/**
 * Transaction type selector chip (Income, Expense, Transfer) with semantic colors.
 */
@Composable
fun TransactionTypeChip(
    type: TransactionType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "type_chip_${type.name.lowercase()}"
) {
    val (typeColor, typeLabel) = when (type) {
        TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive to "Income"
        TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative to "Expense"
        TransactionType.TRANSFER -> ExpenseTrackerTheme.extendedColors.financialNeutral to "Transfer"
    }

    val containerColor = if (selected) {
        typeColor.copy(alpha = 0.2f)
    } else {
        ExpenseTrackerTheme.extendedColors.surfaceHigh
    }

    val borderColor = if (selected) {
        typeColor
    } else {
        ExpenseTrackerTheme.extendedColors.borderSubtle
    }

    val textColor = if (selected) {
        typeColor
    } else {
        ExpenseTrackerTheme.extendedColors.textSecondary
    }

    Box(
        modifier = modifier
            .clip(ExpenseTrackerRadius.chip)
            .background(containerColor)
            .border(1.dp, borderColor, ExpenseTrackerRadius.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = ExpenseTrackerSpacing.lg, vertical = ExpenseTrackerSpacing.sm)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}
