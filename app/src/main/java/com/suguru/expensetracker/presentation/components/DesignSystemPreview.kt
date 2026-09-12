package com.suguru.expensetracker.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

/**
 * Visual design system showcase verifying typography, colors, surfaces, buttons,
 * chips, cards, inputs, and states.
 */
@Composable
fun DesignSystemShowcase() {
    var textFieldValue by remember { mutableStateOf("125.00") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundGlowDecoration()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ExpenseTrackerSpacing.screenHorizontal)
            ) {
                // Header
                Text(
                    text = "Design System",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Purple / Indigo Financial UI Foundation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 1. Hero Financial Card
                SectionHeader(title = "Hero Balance Card")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                HeroFinancialCard(
                    title = "Total Balance",
                    balanceText = "$24,580.42",
                    periodLabel = "This Month",
                    incomeText = "+$5,200.00",
                    expenseText = "-$2,140.18"
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 2. Metric Cards
                SectionHeader(title = "Metric Cards")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    MetricCard(
                        title = "Monthly Savings",
                        value = "$3,060",
                        subtitle = "+12% vs last month",
                        valueColor = ExpenseTrackerTheme.extendedColors.financialPositive,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Active Budgets",
                        value = "4 of 6",
                        subtitle = "1 near limit",
                        valueColor = ExpenseTrackerTheme.extendedColors.financialWarning,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 3. Progress Bars
                SectionHeader(title = "Progress Bars")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Text(
                    text = "Normal (Purple Gradient)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                ExpenseTrackerProgressBar(progress = 0.45f, variant = ProgressVariant.NORMAL)

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Text(
                    text = "Warning (85% Limit)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                ExpenseTrackerProgressBar(progress = 0.85f, variant = ProgressVariant.WARNING)

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Text(
                    text = "Exceeded (105% Over Budget)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                ExpenseTrackerProgressBar(progress = 1.0f, variant = ProgressVariant.EXCEEDED)

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 4. Buttons
                SectionHeader(title = "Button System")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    PrimaryButton(
                        text = "Primary Action",
                        onClick = {},
                        leadingIcon = Icons.Default.Add,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "Secondary",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DangerButton(
                        text = "Delete Item",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                    TertiaryButton(
                        text = "Text Button",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                    AppIconButton(
                        icon = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 5. Input Fields
                SectionHeader(title = "Inputs & Search")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                ExpenseTrackerSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search transactions, categories..."
                )
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.md))
                ExpenseTrackerTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = "Amount",
                    placeholder = "0.00"
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 6. Chips
                SectionHeader(title = "Chip System")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    ExpenseTrackerFilterChip(
                        text = "All",
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 }
                    )
                    ExpenseTrackerFilterChip(
                        text = "Income",
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 }
                    )
                    ExpenseTrackerFilterChip(
                        text = "Expense",
                        selected = selectedFilter == 2,
                        onClick = { selectedFilter = 2 }
                    )
                }
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                ) {
                    TransactionType.entries.forEach { type ->
                        TransactionTypeChip(
                            type = type,
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 7. Financial List Rows
                SectionHeader(title = "List Rows")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                ListCard {
                    FinancialListRow(
                        title = "Grocery Market",
                        subtitle = "Groceries • Chase Checking",
                        amountText = "-$84.20",
                        amountColor = ExpenseTrackerTheme.extendedColors.financialNegative,
                        icon = Icons.Default.ShoppingCart,
                        iconTint = ExpenseTrackerTheme.extendedColors.financialNegative,
                        showChevron = true
                    )
                    FinancialListRow(
                        title = "Salary Deposit",
                        subtitle = "Income • Bi-weekly",
                        amountText = "+$2,600.00",
                        amountColor = ExpenseTrackerTheme.extendedColors.financialPositive,
                        icon = Icons.Default.TrendingUp,
                        iconTint = ExpenseTrackerTheme.extendedColors.financialPositive,
                        isRecurring = true,
                        showChevron = true
                    )
                    FinancialListRow(
                        title = "Savings Transfer",
                        subtitle = "Transfer • High Yield Savings",
                        amountText = "$500.00",
                        amountColor = ExpenseTrackerTheme.extendedColors.financialNeutral,
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = ExpenseTrackerTheme.extendedColors.financialNeutral,
                        showChevron = true
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))

                // 8. Error Banner
                SectionHeader(title = "Error States")
                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))
                ErrorBanner(
                    message = "Could not sync recurring ledger.",
                    onRetry = {}
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.huge))
            }
        }
    }
}

@Preview(name = "Dark Theme Preview", showBackground = true)
@Composable
private fun DesignSystemPreviewDark() {
    ExpenseTrackerTheme(darkTheme = true) {
        DesignSystemShowcase()
    }
}

@Preview(name = "Light Theme Preview", showBackground = true)
@Composable
private fun DesignSystemPreviewLight() {
    ExpenseTrackerTheme(darkTheme = false) {
        DesignSystemShowcase()
    }
}
