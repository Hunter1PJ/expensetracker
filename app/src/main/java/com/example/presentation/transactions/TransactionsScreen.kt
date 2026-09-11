package com.example.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.TransactionType
import com.example.presentation.components.BackgroundGlowDecoration
import com.example.presentation.components.EmptyState
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.ExpenseTrackerFilterChip
import com.example.presentation.components.ExpenseTrackerSearchField
import com.example.presentation.components.FinancialListRow
import com.example.presentation.components.PrimaryButton
import com.example.presentation.components.SecondaryButton
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TransactionsScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onTypeFilterChanged = viewModel::onTypeFilterChanged,
        onAccountFilterChanged = viewModel::onAccountFilterChanged,
        onCategoryFilterChanged = viewModel::onCategoryFilterChanged,
        onClearFilters = viewModel::clearFilters,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToAddTransaction = onNavigateToAddTransaction,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreenContent(
    uiState: TransactionsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onTypeFilterChanged: (TransactionTypeFilter) -> Unit,
    onAccountFilterChanged: (Long?) -> Unit,
    onCategoryFilterChanged: (Long?) -> Unit,
    onClearFilters: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BackgroundGlowDecoration()

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("transactions_screen"),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToAddTransaction,
                    containerColor = ExpenseTrackerTheme.extendedColors.primaryPurple,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("add_transaction_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.title_add_transaction)
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = ExpenseTrackerSpacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .padding(top = ExpenseTrackerSpacing.sm, bottom = ExpenseTrackerSpacing.sm)
                ) {
                    Text(
                        text = "ACTIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = ExpenseTrackerTheme.extendedColors.accentViolet
                    )
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                    Text(
                        text = stringResource(R.string.nav_transactions),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxs))
                    Text(
                        text = "Track where your money goes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))

                // 1. Search Bar (Elevated dark surface, clear action)
                ExpenseTrackerSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    placeholder = stringResource(R.string.search_transactions_hint),
                    onClear = { onSearchQueryChanged("") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp),
                    testTag = "transaction_search_input"
                )

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                // 2. Type Filter Row (All, Expense, Income, Transfer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val types = listOf(
                        TransactionTypeFilter.ALL to stringResource(R.string.filter_all),
                        TransactionTypeFilter.EXPENSE to stringResource(R.string.type_expense),
                        TransactionTypeFilter.INCOME to stringResource(R.string.type_income),
                        TransactionTypeFilter.TRANSFER to stringResource(R.string.type_transfer)
                    )

                    types.forEach { (type, label) ->
                        val isSelected = uiState.selectedType == type
                        ExpenseTrackerFilterChip(
                            text = label,
                            selected = isSelected,
                            onClick = { onTypeFilterChanged(type) },
                            testTag = "type_filter_${type.name.lowercase()}"
                        )
                    }
                }

                // 3. Secondary Filter Row (Accounts & Categories)
                if (uiState.accounts.isNotEmpty() || uiState.categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Account Filter Chips
                        if (uiState.accounts.isNotEmpty()) {
                            val isAllAccountsSelected = uiState.selectedAccountId == null
                            ExpenseTrackerFilterChip(
                                text = stringResource(R.string.filter_all_accounts),
                                selected = isAllAccountsSelected,
                                onClick = { onAccountFilterChanged(null) },
                                testTag = "account_filter_all"
                            )

                            uiState.accounts.forEach { account ->
                                val isSelected = uiState.selectedAccountId == account.id
                                ExpenseTrackerFilterChip(
                                    text = account.name,
                                    selected = isSelected,
                                    onClick = { onAccountFilterChanged(account.id) },
                                    testTag = "account_filter_${account.id}"
                                )
                            }
                        }

                        // Category Filter Chips
                        if (uiState.categories.isNotEmpty() && uiState.selectedType != TransactionTypeFilter.TRANSFER) {
                            val isAllCategoriesSelected = uiState.selectedCategoryId == null
                            ExpenseTrackerFilterChip(
                                text = stringResource(R.string.filter_all_categories),
                                selected = isAllCategoriesSelected,
                                onClick = { onCategoryFilterChanged(null) },
                                testTag = "category_filter_all"
                            )

                            uiState.categories.forEach { category ->
                                val isSelected = uiState.selectedCategoryId == category.id
                                ExpenseTrackerFilterChip(
                                    text = category.name,
                                    selected = isSelected,
                                    onClick = { onCategoryFilterChanged(category.id) },
                                    testTag = "category_filter_${category.id}"
                                )
                            }
                        }
                    }
                }

                // Active filter summary & clear action
                val hasActiveFilters = uiState.selectedType != TransactionTypeFilter.ALL ||
                        uiState.selectedAccountId != null ||
                        uiState.selectedCategoryId != null ||
                        uiState.searchQuery.isNotBlank()

                if (hasActiveFilters && uiState.hasAnyTransactionsInDb) {
                    Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 540.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SecondaryButton(
                            text = stringResource(R.string.action_clear_filters),
                            onClick = onClearFilters,
                            leadingIcon = Icons.Default.Clear,
                            modifier = Modifier.testTag("clear_filters_button")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.sm))

                // 4. Content Area: List / Loading / Empty
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ExpenseTrackerTheme.extendedColors.primaryPurple,
                            modifier = Modifier.testTag("transactions_loading_indicator")
                        )
                    }
                } else if (!uiState.hasAnyTransactionsInDb) {
                    // Database is completely empty
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg),
                            modifier = Modifier.padding(ExpenseTrackerSpacing.xl)
                        ) {
                            EmptyState(
                                title = stringResource(R.string.no_transactions_title),
                                description = stringResource(R.string.no_transactions_desc),
                                icon = Icons.Default.ReceiptLong,
                                testTag = "empty_transactions_state"
                            )
                            PrimaryButton(
                                text = stringResource(R.string.title_add_transaction),
                                onClick = onNavigateToAddTransaction,
                                leadingIcon = Icons.Default.Add,
                                modifier = Modifier.testTag("empty_add_transaction_button")
                            )
                        }
                    }
                } else if (uiState.groupedTransactions.isEmpty()) {
                    // Search or filters returned 0 results
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md),
                            modifier = Modifier.padding(ExpenseTrackerSpacing.xl)
                        ) {
                            EmptyState(
                                title = stringResource(R.string.no_matching_transactions_title),
                                description = stringResource(R.string.no_matching_transactions_desc),
                                icon = Icons.Default.FilterAltOff,
                                testTag = "no_matching_transactions_state"
                            )
                            SecondaryButton(
                                text = stringResource(R.string.action_clear_filters),
                                onClick = onClearFilters,
                                modifier = Modifier.testTag("clear_filters_button")
                            )
                        }
                    }
                } else {
                    // Display grouped transactions in LazyColumn with shared date cards
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .widthIn(max = 540.dp)
                            .testTag("transactions_list"),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        uiState.groupedTransactions.forEach { dateGroup ->
                            item(key = "header_${dateGroup.date}") {
                                DateGroupHeader(title = dateGroup.headerTitle)
                            }

                            item(key = "group_${dateGroup.date}") {
                                ExpenseTrackerCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(ExpenseTrackerRadius.lg),
                                    containerColor = ExpenseTrackerTheme.extendedColors.surface,
                                    borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        dateGroup.transactions.forEachIndexed { index, item ->
                                            FinancialListRow(
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                trailingSubtitle = item.timeFormatted,
                                                amountText = item.formattedAmount,
                                                amountColor = when (item.isPositive) {
                                                    true -> ExpenseTrackerTheme.extendedColors.financialPositive
                                                    false -> ExpenseTrackerTheme.extendedColors.financialNegative
                                                    null -> ExpenseTrackerTheme.extendedColors.financialNeutral
                                                },
                                                icon = when (item.type) {
                                                    TransactionType.EXPENSE -> Icons.Default.ArrowDownward
                                                    TransactionType.INCOME -> Icons.Default.ArrowUpward
                                                    TransactionType.TRANSFER -> Icons.AutoMirrored.Filled.CompareArrows
                                                },
                                                iconTint = when (item.type) {
                                                    TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative
                                                    TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive
                                                    TransactionType.TRANSFER -> ExpenseTrackerTheme.extendedColors.financialNeutral
                                                },
                                                iconBackground = when (item.type) {
                                                    TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative.copy(alpha = 0.14f)
                                                    TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.14f)
                                                    TransactionType.TRANSFER -> ExpenseTrackerTheme.extendedColors.financialNeutral.copy(alpha = 0.14f)
                                                },
                                                isRecurring = item.isRecurring,
                                                onClick = { onNavigateToDetail(item.id) },
                                                testTag = "transaction_row_${item.id}"
                                            )

                                            if (index < dateGroup.transactions.lastIndex) {
                                                HorizontalDivider(
                                                    color = ExpenseTrackerTheme.extendedColors.borderSubtle,
                                                    thickness = 0.5.dp,
                                                    modifier = Modifier.padding(horizontal = ExpenseTrackerSpacing.lg)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateGroupHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = ExpenseTrackerSpacing.md,
                bottom = ExpenseTrackerSpacing.xs,
                start = ExpenseTrackerSpacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = ExpenseTrackerTheme.extendedColors.accentViolet
        )
    }
}
