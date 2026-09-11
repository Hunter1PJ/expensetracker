package com.example.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.Transaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.ObserveAllAccountsUseCase
import com.example.domain.usecase.category.ObserveAllCategoriesUseCase
import com.example.domain.usecase.transaction.ObserveTransactionsUseCase
import com.example.domain.util.MoneyParser
import com.example.presentation.util.DateTimeFormatterHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

private data class FilterParams(
    val query: String,
    val typeFilter: TransactionTypeFilter,
    val accountFilterId: Long?,
    val categoryFilterId: Long?
)

/**
 * ViewModel managing the full Transaction History list, search, and multi-faceted filtering.
 * Reactively joins transaction data with accounts and categories to avoid N+1 queries.
 */
class TransactionsViewModel(
    private val observeTransactionsUseCase: ObserveTransactionsUseCase,
    private val observeAllAccountsUseCase: ObserveAllAccountsUseCase,
    private val observeAllCategoriesUseCase: ObserveAllCategoriesUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedType = MutableStateFlow(TransactionTypeFilter.ALL)
    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)

    private val _filterParams = combine(
        _searchQuery,
        _selectedType,
        _selectedAccountId,
        _selectedCategoryId
    ) { query, type, accountId, categoryId ->
        FilterParams(
            query = query,
            typeFilter = type,
            accountFilterId = accountId,
            categoryFilterId = categoryId
        )
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        observeTransactionsUseCase(),
        observeAllAccountsUseCase(),
        observeAllCategoriesUseCase(),
        _filterParams
    ) { transactions: List<Transaction>, accounts: List<Account>, categories: List<Category>, filter: FilterParams ->

        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        // Filter transactions
        val filteredTransactions = transactions.filter { tx ->
            // 1. Type filter
            val matchesType = when (filter.typeFilter) {
                TransactionTypeFilter.ALL -> true
                TransactionTypeFilter.EXPENSE -> tx.type == TransactionType.EXPENSE
                TransactionTypeFilter.INCOME -> tx.type == TransactionType.INCOME
                TransactionTypeFilter.TRANSFER -> tx.type == TransactionType.TRANSFER
            }
            if (!matchesType) return@filter false

            // 2. Account filter
            val matchesAccount = if (filter.accountFilterId == null) {
                true
            } else {
                when (tx.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME -> tx.accountId == filter.accountFilterId
                    TransactionType.TRANSFER -> tx.accountId == filter.accountFilterId || tx.destinationAccountId == filter.accountFilterId
                }
            }
            if (!matchesAccount) return@filter false

            // 3. Category filter
            val matchesCategory = if (filter.categoryFilterId == null) {
                true
            } else {
                when (tx.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME -> tx.categoryId == filter.categoryFilterId
                    TransactionType.TRANSFER -> false // Transfers do not have categories
                }
            }
            if (!matchesCategory) return@filter false

            // 4. Search query filter
            if (filter.query.isNotBlank()) {
                val cleanQuery = filter.query.trim().lowercase()
                val sourceAccount = accountMap[tx.accountId]
                val destAccount = tx.destinationAccountId?.let { accountMap[it] }
                val category = tx.categoryId?.let { categoryMap[it] }

                val noteMatches = tx.note?.lowercase()?.contains(cleanQuery) == true
                val sourceAccountMatches = sourceAccount?.name?.lowercase()?.contains(cleanQuery) == true
                val destAccountMatches = destAccount?.name?.lowercase()?.contains(cleanQuery) == true
                val categoryMatches = category?.name?.lowercase()?.contains(cleanQuery) == true
                val typeMatches = tx.type.name.lowercase().contains(cleanQuery)

                noteMatches || sourceAccountMatches || destAccountMatches || categoryMatches || typeMatches
            } else {
                true
            }
        }

        // Sort descending (newest first)
        val sortedTransactions = filteredTransactions.sortedByDescending { it.transactionTime }

        // Group by LocalDate
        val groupedMap = sortedTransactions.groupBy { tx ->
            tx.transactionTime.atZone(zoneId).toLocalDate()
        }

        val today = LocalDate.now(zoneId)
        val dateGroups = groupedMap.map { (date, txs) ->
            val uiItems = txs.map { tx ->
                val sourceAccount = accountMap[tx.accountId]
                val destAccount = tx.destinationAccountId?.let { accountMap[it] }
                val category = tx.categoryId?.let { categoryMap[it] }

                mapToListItemUiModel(
                    tx = tx,
                    sourceAccount = sourceAccount,
                    destAccount = destAccount,
                    category = category,
                    zoneId = zoneId
                )
            }

            TransactionDateGroupUiModel(
                date = date,
                headerTitle = DateTimeFormatterHelper.formatGroupDateHeader(date, zoneId, today),
                transactions = uiItems
            )
        }

        TransactionsUiState(
            isLoading = false,
            searchQuery = filter.query,
            selectedType = filter.typeFilter,
            selectedAccountId = filter.accountFilterId,
            selectedCategoryId = filter.categoryFilterId,
            accounts = accounts,
            categories = categories,
            groupedTransactions = dateGroups,
            hasAnyTransactionsInDb = transactions.isNotEmpty(),
            errorMessage = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTypeFilterChanged(type: TransactionTypeFilter) {
        _selectedType.value = type
    }

    fun onAccountFilterChanged(accountId: Long?) {
        _selectedAccountId.value = accountId
    }

    fun onCategoryFilterChanged(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedType.value = TransactionTypeFilter.ALL
        _selectedAccountId.value = null
        _selectedCategoryId.value = null
    }

    private fun mapToListItemUiModel(
        tx: Transaction,
        sourceAccount: Account?,
        destAccount: Account?,
        category: Category?,
        zoneId: ZoneId
    ): TransactionListItemUiModel {
        val timeFormatted = DateTimeFormatterHelper.formatTimeOnly(tx.transactionTime, zoneId)
        val localDate = tx.transactionTime.atZone(zoneId).toLocalDate()

        return when (tx.type) {
            TransactionType.EXPENSE -> {
                val formattedAmount = "- ${MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true, showExplicitSign = false)}"
                val title = category?.name ?: "Expense"
                val subtitle = buildString {
                    append(sourceAccount?.name ?: "Account")
                    if (!tx.note.isNullOrBlank()) {
                        append(" • ")
                        append(tx.note)
                    }
                }

                TransactionListItemUiModel(
                    id = tx.id,
                    type = tx.type,
                    formattedAmount = formattedAmount,
                    isPositive = false,
                    title = title,
                    subtitle = subtitle,
                    iconName = category?.iconName,
                    colorHex = category?.colorHex,
                    timeFormatted = timeFormatted,
                    rawInstant = tx.transactionTime,
                    rawDate = localDate,
                    isRecurring = tx.recurringRuleId != null
                )
            }
            TransactionType.INCOME -> {
                val formattedAmount = "+ ${MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true, showExplicitSign = false)}"
                val title = category?.name ?: "Income"
                val subtitle = buildString {
                    append(sourceAccount?.name ?: "Account")
                    if (!tx.note.isNullOrBlank()) {
                        append(" • ")
                        append(tx.note)
                    }
                }

                TransactionListItemUiModel(
                    id = tx.id,
                    type = tx.type,
                    formattedAmount = formattedAmount,
                    isPositive = true,
                    title = title,
                    subtitle = subtitle,
                    iconName = category?.iconName,
                    colorHex = category?.colorHex,
                    timeFormatted = timeFormatted,
                    rawInstant = tx.transactionTime,
                    rawDate = localDate,
                    isRecurring = tx.recurringRuleId != null
                )
            }
            TransactionType.TRANSFER -> {
                val formattedAmount = MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true, showExplicitSign = false)
                val fromName = sourceAccount?.name ?: "Account"
                val toName = destAccount?.name ?: "Account"
                val title = "$fromName → $toName"
                val subtitle = if (!tx.note.isNullOrBlank()) tx.note else "Transfer"

                TransactionListItemUiModel(
                    id = tx.id,
                    type = tx.type,
                    formattedAmount = formattedAmount,
                    isPositive = null,
                    title = title,
                    subtitle = subtitle,
                    iconName = null,
                    colorHex = null,
                    timeFormatted = timeFormatted,
                    rawInstant = tx.transactionTime,
                    rawDate = localDate,
                    isRecurring = tx.recurringRuleId != null
                )
            }
        }
    }
}
