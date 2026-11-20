package com.suguru.expensetracker.presentation.transactions

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.EmptyState
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.ExpenseTrackerFilterChip
import com.suguru.expensetracker.presentation.components.ExpenseTrackerSearchField
import com.suguru.expensetracker.presentation.components.FinancialListRow
import com.suguru.expensetracker.presentation.components.SecondaryButton
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TransactionsScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onTypeFilterChanged = viewModel::onTypeFilterChanged,
        onCategoryFilterChanged = viewModel::onCategoryFilterChanged,
        onClearFilters = viewModel::clearFilters,
        onNavigateToDetail = onNavigateToDetail,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreenContent(
    uiState: TransactionsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onTypeFilterChanged: (TransactionTypeFilter) -> Unit,
    onCategoryFilterChanged: (Long?) -> Unit,
    onClearFilters: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveFilters = uiState.selectedType != TransactionTypeFilter.ALL ||
        uiState.selectedCategoryId != null || uiState.searchQuery.isNotBlank()

    Box(modifier = modifier.fillMaxSize().testTag("transactions_screen")) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("transactions_list"),
        contentPadding = PaddingValues(
            horizontal = ExpenseTrackerSpacing.screenHorizontal,
            top = ExpenseTrackerSpacing.xl,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Archive identity and search are a single scrollable header, matching the journal design.
        item(key = "transactions_header") {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)) {
                    Text(
                        text = stringResource(R.string.transaction_activity_heading),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.nav_transactions),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.transaction_activity_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
                ExpenseTrackerSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    placeholder = stringResource(R.string.search_transactions_hint),
                    onClear = { onSearchQueryChanged("") },
                    modifier = Modifier.fillMaxWidth().testTag("transaction_search_input"),
                    testTag = "transaction_search_input"
                )
            }
        }

        // Filters remain directly below search; Transfer is retained only as a historical filter.
        item(key = "transaction_filters") {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                        ExpenseTrackerFilterChip(
                            text = label,
                            selected = uiState.selectedType == type,
                            onClick = { onTypeFilterChanged(type) },
                            testTag = "type_filter_${type.name.lowercase()}"
                        )
                    }
                }

                if (uiState.categories.isNotEmpty() && uiState.selectedType != TransactionTypeFilter.TRANSFER) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpenseTrackerFilterChip(
                            text = stringResource(R.string.filter_all_categories),
                            selected = uiState.selectedCategoryId == null,
                            onClick = { onCategoryFilterChanged(null) },
                            testTag = "category_filter_all"
                        )
                        uiState.categories.forEach { category ->
                            ExpenseTrackerFilterChip(
                                text = com.suguru.expensetracker.presentation.util.categoryDisplayName(category),
                                selected = uiState.selectedCategoryId == category.id,
                                onClick = { onCategoryFilterChanged(category.id) },
                                testTag = "category_filter_${category.id}"
                            )
                        }
                    }
                }

                if (hasActiveFilters && uiState.hasAnyTransactionsInDb) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton(
                            text = stringResource(R.string.action_clear_filters),
                            onClick = onClearFilters,
                            leadingIcon = Icons.Default.Clear,
                            modifier = Modifier.testTag("clear_filters_button")
                        )
                    }
                }
            }
        }

        when {
            uiState.isLoading -> item(key = "transactions_loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("transactions_loading_indicator")
                    )
                }
            }

            !uiState.hasAnyTransactionsInDb -> item(key = "transactions_empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = stringResource(R.string.no_transactions_title),
                        description = stringResource(R.string.no_transactions_desc),
                        icon = Icons.Default.ReceiptLong,
                        testTag = "empty_transactions_state"
                    )
                }
            }

            uiState.groupedTransactions.isEmpty() -> item(key = "transactions_no_match") {
                Column(
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(ExpenseTrackerSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
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

            else -> {
                uiState.groupedTransactions.forEach { dateGroup ->
                    item(key = "header_${dateGroup.date}") {
                        val today = java.time.LocalDate.now()
                        val title = when (dateGroup.date) {
                            today -> stringResource(R.string.date_today)
                            today.minusDays(1) -> stringResource(R.string.date_yesterday)
                            else -> dateGroup.headerTitle
                        }
                        DateGroupHeader(title = title, modifier = Modifier.widthIn(max = 540.dp))
                    }
                    item(key = "group_${dateGroup.date}") {
                        Column(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp)
                                .testTag("transaction_group_${dateGroup.date}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                dateGroup.transactions.forEachIndexed { index, item ->
                                    FinancialListRow(
                                        title = item.title.ifBlank {
                                            when (item.type) {
                                                TransactionType.EXPENSE -> stringResource(R.string.type_expense)
                                                TransactionType.INCOME -> stringResource(R.string.type_income)
                                                TransactionType.TRANSFER -> stringResource(R.string.type_transfer)
                                            }
                                        },
                                        subtitle = item.subtitle.takeIf { it.isNotBlank() }
                                            ?: if (item.type == TransactionType.TRANSFER) stringResource(R.string.type_transfer) else null,
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
                                            TransactionType.EXPENSE -> ExpenseTrackerTheme.extendedColors.financialNegative.copy(alpha = 0.12f)
                                            TransactionType.INCOME -> ExpenseTrackerTheme.extendedColors.financialPositive.copy(alpha = 0.12f)
                                            TransactionType.TRANSFER -> ExpenseTrackerTheme.extendedColors.financialNeutral.copy(alpha = 0.12f)
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
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
