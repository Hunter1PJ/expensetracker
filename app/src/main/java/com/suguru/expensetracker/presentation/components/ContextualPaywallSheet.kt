package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.suguru.expensetracker.domain.model.ProFeature
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualPaywallSheet(
    feature: ProFeature,
    limit: Int,
    onViewPro: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isPending: Boolean = false
) {
    val title = if (isPending) {
        "Your Pro purchase is still pending"
    } else {
        when (feature) {
            ProFeature.ACCOUNTS -> "You've reached the Free account limit"
            ProFeature.CUSTOM_CATEGORIES -> "You've reached the Free custom category limit"
            ProFeature.BUDGETS -> "You've reached the Free budget limit"
            ProFeature.RECURRING_RULES -> "You've reached the Free recurring limit"
        }
    }

    val message = if (isPending) {
        "Google Play is processing your payment."
    } else {
        when (feature) {
            ProFeature.ACCOUNTS -> "Free includes up to $limit active accounts. Upgrade to ExpenseTracker Pro for unlimited accounts."
            ProFeature.CUSTOM_CATEGORIES -> "Free includes up to $limit custom categories. Upgrade to Pro for unlimited custom categories."
            ProFeature.BUDGETS -> "Free includes up to $limit active budgets. Upgrade to Pro for unlimited budgets."
            ProFeature.RECURRING_RULES -> "Free includes up to $limit active recurring rules. Upgrade to Pro for unlimited recurring automation."
        }
    }

    ExpenseTrackerBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("pro_limit_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ExpenseTrackerSpacing.lg, vertical = ExpenseTrackerSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = ExpenseTrackerSpacing.xs)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("pro_limit_title")
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("pro_limit_message")
            )

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                if (isPending) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pro_limit_not_now")
                    ) {
                        Text("Done")
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onViewPro()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pro_limit_view_pro")
                    ) {
                        Text("View Status")
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pro_limit_not_now")
                    ) {
                        Text("Not Now")
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onViewPro()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pro_limit_view_pro")
                    ) {
                        Text("View Pro")
                    }
                }
            }
        }
    }
}
