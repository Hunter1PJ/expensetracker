package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Reusable Confirmation & Action Dialog adhering to the purple/indigo design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    confirmTestTag: String = "confirm_button",
    dismissTestTag: String = "dismiss_button",
    testTag: String = "confirmation_dialog"
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(testTag)
    ) {
        Card(
            shape = ExpenseTrackerRadius.dialog,
            colors = CardDefaults.cardColors(
                containerColor = ExpenseTrackerTheme.extendedColors.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, ExpenseTrackerTheme.extendedColors.borderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ExpenseTrackerSpacing.xxl)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        ExpenseTrackerSpacing.md,
                        alignment = androidx.compose.ui.Alignment.End
                    )
                ) {
                    SecondaryButton(
                        text = dismissText,
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag(dismissTestTag)
                    )

                    if (isDestructive) {
                        DangerButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.testTag(confirmTestTag)
                        )
                    } else {
                        PrimaryButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.testTag(confirmTestTag)
                        )
                    }
                }
            }
        }
    }
}
