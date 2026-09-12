package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Reusable Error Banner / Card component for displaying system or network errors.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = "Error",
    onRetry: (() -> Unit)? = null,
    testTag: String = "error_banner"
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = ExpenseTrackerRadius.card,
        colors = CardDefaults.cardColors(
            containerColor = ExpenseTrackerTheme.extendedColors.financialDangerContainer.copy(alpha = 0.5f),
            contentColor = ExpenseTrackerTheme.extendedColors.textPrimary
        ),
        border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.financialDanger.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ExpenseTrackerSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = ExpenseTrackerTheme.extendedColors.financialDanger,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.lg)
            )

            Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseTrackerTheme.extendedColors.financialDanger
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textPrimary
                )
            }

            if (onRetry != null) {
                Spacer(modifier = Modifier.width(ExpenseTrackerSpacing.sm))
                TextButton(onClick = onRetry) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseTrackerTheme.extendedColors.financialDanger
                    )
                }
            }
        }
    }
}
