package com.example.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Account
import com.example.domain.model.Category
import com.example.domain.model.RecurringTransaction
import com.example.domain.model.TransactionType
import com.example.domain.usecase.account.ObserveAllAccountsUseCase
import com.example.domain.usecase.category.ObserveAllCategoriesUseCase
import com.example.domain.usecase.recurring.DeactivateRecurringTransactionUseCase
import com.example.domain.usecase.recurring.ObserveRecurringTransactionsUseCase
import com.example.domain.usecase.recurring.ProcessDueRecurringTransactionsUseCase
import com.example.domain.util.MoneyParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface RecurringTransactionsUiState {
    data object Loading : RecurringTransactionsUiState
    data class Success(val rules: List<RecurringTransactionUiModel>) : RecurringTransactionsUiState
    data class Error(val message: String) : RecurringTransactionsUiState
}

class RecurringTransactionsViewModel(
    private val observeRecurringTransactionsUseCase: ObserveRecurringTransactionsUseCase,
    private val observeAllAccountsUseCase: ObserveAllAccountsUseCase,
    private val observeAllCategoriesUseCase: ObserveAllCategoriesUseCase,
    private val deactivateRecurringTransactionUseCase: DeactivateRecurringTransactionUseCase,
    private val processDueRecurringTransactionsUseCase: ProcessDueRecurringTransactionsUseCase
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)

    val uiState: StateFlow<RecurringTransactionsUiState> = combine(
        observeRecurringTransactionsUseCase(onlyActive = false),
        observeAllAccountsUseCase(),
        observeAllCategoriesUseCase()
    ) { rules, accounts, categories ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        val uiModels = rules.map { rule ->
            mapToUiModel(rule, accountMap, categoryMap)
        }

        RecurringTransactionsUiState.Success(uiModels)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringTransactionsUiState.Loading
    )

    fun deactivateRule(id: Long) {
        viewModelScope.launch {
            try {
                deactivateRecurringTransactionUseCase(id)
            } catch (_: Exception) {
            }
        }
    }

    fun processDueNow() {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                processDueRecurringTransactionsUseCase(asOfDate = LocalDate.now())
            } catch (_: Exception) {
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun mapToUiModel(
        rule: RecurringTransaction,
        accountMap: Map<Long, Account>,
        categoryMap: Map<Long, Category>
    ): RecurringTransactionUiModel {
        val sourceAccount = accountMap[rule.accountId]
        val destAccount = rule.destinationAccountId?.let { accountMap[it] }
        val category = rule.categoryId?.let { categoryMap[it] }

        val formattedAmount = MoneyParser.format(rule.amount)

        val needsAttention = when (rule.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                sourceAccount == null || sourceAccount.isArchived || category == null || category.isArchived
            }
            TransactionType.TRANSFER -> {
                sourceAccount == null || sourceAccount.isArchived || destAccount == null || destAccount.isArchived
            }
        }

        val status = when {
            !rule.isActive -> RecurringStatus.PAUSED
            needsAttention -> RecurringStatus.NEEDS_ATTENTION
            rule.endDate != null && rule.endDate <= LocalDate.now().plusDays(14) -> RecurringStatus.ENDING_SOON
            else -> RecurringStatus.ACTIVE
        }

        val statusDetail = when {
            sourceAccount == null -> "Source account missing"
            sourceAccount.isArchived -> "Source account is archived"
            rule.type == TransactionType.TRANSFER && destAccount == null -> "Destination account missing"
            rule.type == TransactionType.TRANSFER && destAccount?.isArchived == true -> "Destination account is archived"
            rule.type != TransactionType.TRANSFER && category == null -> "Category missing"
            rule.type != TransactionType.TRANSFER && category?.isArchived == true -> "Category is archived"
            else -> null
        }

        return RecurringTransactionUiModel(
            id = rule.id,
            type = rule.type,
            formattedAmount = formattedAmount,
            accountName = sourceAccount?.name ?: "Unknown Account",
            destinationAccountName = destAccount?.name,
            categoryName = category?.name,
            categoryIcon = category?.iconName,
            frequency = rule.frequency,
            startDate = rule.startDate,
            endDate = rule.endDate,
            nextOccurrence = rule.nextOccurrence,
            note = rule.note,
            isActive = rule.isActive,
            status = status,
            statusDetail = statusDetail
        )
    }
}
