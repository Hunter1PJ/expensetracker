package com.example.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

/**
 * Reusable Financial List Row for transactions, accounts, recurring items, and category lists.
 */
@Composable
fun FinancialListRow(
    title: String,
    amountText: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingSubtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = iconTint.copy(alpha = 0.16f),
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    isRecurring: Boolean = false,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String = "financial_list_row"
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = ExpenseTrackerSpacing.lg,
                vertical = ExpenseTrackerSpacing.md
            )
            .testTag(testTag)
    } else {
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = ExpenseTrackerSpacing.lg,
                vertical = ExpenseTrackerSpacing.md
            )
            .testTag(testTag)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconAvatar(
                icon = icon,
                contentDescription = title,
                tint = iconTint,
                backgroundColor = iconBackground,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isRecurring) {
                    Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Recurring",
                        tint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                        modifier = Modifier.size(ExpenseTrackerTheme.iconSize.xs)
                    )
                }
            }

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            if (trailingSubtitle != null) {
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                Text(
                    text = trailingSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textTertiary
                )
            }
        }

        if (showChevron) {
            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.xs))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ExpenseTrackerTheme.extendedColors.textMuted,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
            )
        }
    }
}
